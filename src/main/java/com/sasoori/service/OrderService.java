package com.sasoori.service;

import com.sasoori.config.AppConfig;
import com.sasoori.dao.AddressDao;
import com.sasoori.dao.CartDao;
import com.sasoori.dao.OrderDao;
import com.sasoori.dao.PaymentDao;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Address;
import com.sasoori.model.CartItem;
import com.sasoori.model.Order;
import com.sasoori.model.OrderItem;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates order placement, payment verification, and cancellation.
 */
public class OrderService {

    private static final Logger log = LogManager.getLogger(OrderService.class);

    private final AppConfig        cfg;
    private final OrderDao         orderDao;
    private final CartDao          cartDao;
    private final AddressDao       addressDao;
    private final RazorpayService  razorpayService;
    private final PaymentDao       paymentDao;

    public OrderService(AppConfig cfg,
                        OrderDao orderDao,
                        CartDao cartDao,
                        AddressDao addressDao,
                        RazorpayService razorpayService,
                        PaymentDao paymentDao) {
        this.cfg            = cfg;
        this.orderDao       = orderDao;
        this.cartDao        = cartDao;
        this.addressDao     = addressDao;
        this.razorpayService = razorpayService;
        this.paymentDao     = paymentDao;
    }

    // ── Place order ───────────────────────────────────────────────────────

    /**
     * Full order placement pipeline:
     * validate → price → Razorpay order → DB insert (SERIALIZABLE) → clear cart
     */
    public PlaceOrderResult placeOrder(String userId,
                                        int addressId,
                                        String notes) throws SQLException {
        // 1. Verify address belongs to user
        Address address = addressDao.findById(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("Delivery address not found"));

        // 2. Load cart
        List<CartItem> cartItems = cartDao.getCartItems(userId);
        if (cartItems.isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "Your cart is empty");
        }

        // 3. Compute totals
        int subtotal = cartItems.stream()
                .mapToInt(ci -> ci.getPricePaise() * ci.getQuantity())
                .sum();
        int shipping = subtotal >= cfg.freeShippingThresholdPaise ? 0 : cfg.shippingChargePaise;
        int total    = subtotal + shipping;

        // 4. Create Razorpay order
        String receiptId = "SAR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String razorpayOrderId = razorpayService.createOrder(total, "INR", receiptId);

        // 5. Build domain objects
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus("PENDING");
        order.setSubtotalPaise(subtotal);
        order.setShippingPaise(shipping);
        order.setDiscountPaise(0);
        order.setTotalPaise(total);
        order.setShippingAddress(JsonUtil.toJson(address)); // snapshot
        order.setRazorpayOrderId(razorpayOrderId);
        order.setNotes(notes);

        List<OrderItem> items = new ArrayList<>();
        for (CartItem ci : cartItems) {
            OrderItem oi = new OrderItem();
            oi.setProductId(ci.getProductId());
            oi.setProductName(ci.getProductName());
            oi.setProductSku(ci.getProductSku());
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPricePaise(ci.getPricePaise());
            oi.setTotalPaise(ci.getPricePaise() * ci.getQuantity());
            items.add(oi);
        }

        // 6. Persist (SERIALIZABLE: stock check + decrement inside)
        Order saved = orderDao.create(order, items);

        // 7. Clear cart
        cartDao.clearCart(userId);

        log.info("Order placed orderId={} userId={} total={}p", saved.getId(), userId, total);
        return new PlaceOrderResult(saved.getId(), razorpayOrderId, total, "INR");
    }

    // ── Verify payment ────────────────────────────────────────────────────

    /**
     * Verifies the Razorpay payment signature and updates order + payment status.
     */
    public void verifyPayment(String userId,
                               String orderId,
                               String razorpayPaymentId,
                               String razorpaySignature) throws SQLException {
        // Verify order belongs to user
        orderDao.findById(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));

        // Get the Razorpay order_id stored in payments table
        String razorpayOrderId = paymentDao.getRazorpayOrderId(orderId);
        if (razorpayOrderId == null) {
            throw ApiException.notFound("Payment record not found for order");
        }

        // Verify signature
        if (!razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            throw ApiException.badRequest("INVALID_PAYMENT_SIGNATURE",
                    "Payment signature verification failed");
        }

        // Update DB
        paymentDao.updateCaptured(orderId, razorpayPaymentId, razorpaySignature);
        orderDao.updateStatus(orderId, "PAID");

        log.info("Payment verified orderId={} paymentId={}", orderId, razorpayPaymentId);
    }

    // ── Cancel order ──────────────────────────────────────────────────────

    /**
     * Cancels an order if it's in a cancellable state (PENDING or PAID).
     * Restores stock in a transaction.
     */
    public void cancelOrder(String userId, String orderId) throws SQLException {
        Order order = orderDao.findById(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));

        String status = order.getStatus();
        if (!"PENDING".equals(status) && !"PAID".equals(status)) {
            throw ApiException.badRequest("CANNOT_CANCEL",
                    "Order cannot be cancelled in status: " + status);
        }

        orderDao.cancelWithStockRestore(orderId);
        log.info("Order cancelled orderId={} userId={}", orderId, userId);
    }

    // ── Result type ───────────────────────────────────────────────────────

    public record PlaceOrderResult(String orderId, String razorpayOrderId,
                                    int totalPaise, String currency) {}
}
