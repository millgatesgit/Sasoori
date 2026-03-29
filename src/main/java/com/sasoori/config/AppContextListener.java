package com.sasoori.config;

import com.sasoori.dao.AddressDao;
import com.sasoori.dao.CartDao;
import com.sasoori.dao.ShipmentDao;
import com.sasoori.dao.CategoryDao;
import com.sasoori.dao.OrderDao;
import com.sasoori.dao.OtpVerificationDao;
import com.sasoori.dao.PaymentDao;
import com.sasoori.dao.ProductDao;
import com.sasoori.dao.RefreshTokenDao;
import com.sasoori.dao.UserDao;
import com.sasoori.service.GoogleOAuthService;
import com.sasoori.service.OrderService;
import com.sasoori.service.OtpService;
import com.sasoori.service.ProductService;
import com.sasoori.service.RazorpayService;
import com.sasoori.service.ShiprocketService;
import com.sasoori.service.SmsService;
import com.sasoori.service.TokenService;
import com.sasoori.service.WhatsAppService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bootstrap listener — wires all singletons into the ServletContext so that
 * filters and servlets can retrieve them via {@code ctx.getAttribute("name")}.
 *
 * <p>Startup order: PostgreSQL → HikariCP → Services → Servlet init
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger log = LogManager.getLogger(AppContextListener.class);

    private DatabaseConfig dbConfig;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Sasoori backend starting up...");
        ServletContext ctx = sce.getServletContext();

        try {
            // ── Configuration ─────────────────────────────────────────────
            AppConfig cfg = AppConfig.get();
            log.info("Config loaded, devMode={}", cfg.devMode);

            // Expose CORS origin and cookie flag as init params for filters
            ctx.setInitParameter("frontendUrl",  cfg.frontendUrl);
            ctx.setInitParameter("secureCookie", String.valueOf(cfg.secureCookie));

            // ── Database ──────────────────────────────────────────────────
            dbConfig = new DatabaseConfig(cfg);
            log.info("Database pool initialized");

            // ── DAOs ──────────────────────────────────────────────────────
            UserDao         userDao  = new UserDao(dbConfig.getDataSource());
            RefreshTokenDao rtDao    = new RefreshTokenDao(dbConfig.getDataSource());

            // ── Services ──────────────────────────────────────────────────
            TokenService       tokenService  = new TokenService(cfg, rtDao);
            GoogleOAuthService googleOAuth   = new GoogleOAuthService(cfg);

            // ── OTP / SMS ─────────────────────────────────────────────────
            OtpVerificationDao otpDao    = new OtpVerificationDao(dbConfig.getDataSource());
            SmsService         smsService = new SmsService(cfg);
            OtpService         otpService = new OtpService(cfg, otpDao, userDao, tokenService, smsService);

            // ── Register in ServletContext (manual DI) ────────────────────
            ctx.setAttribute("userDao",            userDao);
            ctx.setAttribute("refreshTokenDao",    rtDao);
            ctx.setAttribute("tokenService",       tokenService);
            ctx.setAttribute("googleOAuthService", googleOAuth);
            ctx.setAttribute("otpVerificationDao", otpDao);
            ctx.setAttribute("smsService",         smsService);
            ctx.setAttribute("otpService",         otpService);

            // ── Products ──────────────────────────────────────────────────
            CategoryDao    categoryDao    = new CategoryDao(dbConfig.getDataSource());
            ProductDao     productDao     = new ProductDao(dbConfig.getDataSource());
            ProductService productService = new ProductService(productDao, categoryDao);

            ctx.setAttribute("categoryDao",    categoryDao);
            ctx.setAttribute("productDao",     productDao);
            ctx.setAttribute("productService", productService);

            // ── Cart ──────────────────────────────────────────────────────
            CartDao cartDao = new CartDao(dbConfig.getDataSource());
            ctx.setAttribute("cartDao", cartDao);

            ShipmentDao shipmentDao = new ShipmentDao(dbConfig.getDataSource());
            ctx.setAttribute("shipmentDao", shipmentDao);

            // ── Orders / Payments ─────────────────────────────────────────────
            AddressDao      addressDao      = new AddressDao(dbConfig.getDataSource());
            PaymentDao      paymentDao      = new PaymentDao(dbConfig.getDataSource());
            OrderDao        orderDao        = new OrderDao(dbConfig.getDataSource());
            RazorpayService razorpayService = new RazorpayService(cfg);
            OrderService    orderService    = new OrderService(cfg, orderDao, cartDao,
                                                               addressDao, razorpayService, paymentDao);

            ctx.setAttribute("addressDao",      addressDao);
            ctx.setAttribute("paymentDao",      paymentDao);
            ctx.setAttribute("orderDao",        orderDao);
            ctx.setAttribute("razorpayService", razorpayService);
            ctx.setAttribute("orderService",    orderService);

            // ── Shipping / Notifications ──────────────────────────────────────
            ShiprocketService shiprocketService = new ShiprocketService(cfg);
            WhatsAppService   whatsAppService   = new WhatsAppService(cfg);

            ctx.setAttribute("shiprocketService", shiprocketService);
            ctx.setAttribute("whatsAppService",   whatsAppService);

            log.info("Sasoori backend started successfully");
        } catch (Throwable e) {
            log.error("Fatal: startup failed — {}", e.getMessage(), e);
            // Re-throw to prevent deployment with broken state
            throw new RuntimeException("Sasoori startup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (dbConfig != null) dbConfig.close();
        log.info("Sasoori backend shut down cleanly");
    }
}
