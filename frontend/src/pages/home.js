/**
 * SASOORI — Home Page
 * Route: #/
 */

import { productCardHTML, attachCardEvents, skeletonCards } from '../components/productCard.js';
import { getProducts } from '../api/productApi.js';
import { formatPrice } from '../utils/formatters.js';

const page = {
  meta: () => ({
    title: 'Pure Cold Pressed Oils & Masala Powders',
    description: 'Sasoori — 100% natural cold pressed oils and masala powders. No preservatives. From our home to your home.',
  }),

  async render(container) {
    container.innerHTML = HOME_TEMPLATE;
  },

  async onMount() {
    await Promise.all([
      loadFeaturedProducts(),
    ]);
    initLazyLoad();
  },

  onDestroy() {
    // Nothing to clean up for home page
  },
};

async function loadFeaturedProducts() {
  const grid = document.getElementById('featuredGrid');
  if (!grid) return;

  grid.innerHTML = skeletonCards(8);

  try {
    const result = await getProducts({ size: 8 });
    const products = result.products || result;

    if (!products.length) {
      grid.innerHTML = `<p class="text-muted" style="grid-column:1/-1;text-align:center;padding:var(--sp-8)">No products yet — check back soon!</p>`;
      return;
    }

    grid.innerHTML = products.map(productCardHTML).join('');
    attachCardEvents(grid);
  } catch {
    grid.innerHTML = `<p class="text-muted" style="grid-column:1/-1;text-align:center;padding:var(--sp-8)">Could not load products. Please refresh.</p>`;
  }
}

function initLazyLoad() {
  if (!('IntersectionObserver' in window)) return;
  const imgs = document.querySelectorAll('img[data-src]');
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const img = entry.target;
        img.src = img.dataset.src;
        observer.unobserve(img);
      }
    });
  }, { rootMargin: '200px' });
  imgs.forEach(img => observer.observe(img));
}

// ── Template ───────────────────────────────────────────────────
const HOME_TEMPLATE = `
  <!-- ── HERO ────────────────────────────────────────────── -->
  <section class="hero" style="
    background: linear-gradient(135deg, #006B2E 0%, #00923F 40%, #00923F 70%, #84C225 100%);
    color: white;
    display: flex;
    align-items: center;
    min-height: 480px;
    position: relative;
    overflow: hidden;
  ">
    <!-- Decorative circles -->
    <div style="position:absolute;width:400px;height:400px;background:rgba(255,255,255,0.04);border-radius:50%;top:-100px;right:-100px;pointer-events:none"></div>
    <div style="position:absolute;width:250px;height:250px;background:rgba(255,255,255,0.04);border-radius:50%;bottom:-80px;left:-60px;pointer-events:none"></div>

    <div class="container" style="position:relative;z-index:1;padding-top:var(--sp-12);padding-bottom:var(--sp-12)">
      <div style="max-width:600px">
        <p style="font-size:var(--ts-sm);letter-spacing:0.12em;text-transform:uppercase;opacity:0.75;margin-bottom:var(--sp-3)">
          🌿 100% Pure &amp; Natural
        </p>
        <h1 style="font-size:clamp(2rem,5vw,3.5rem);font-family:var(--font-heading);font-weight:700;line-height:1.15;margin-bottom:var(--sp-4)">
          Pure. Cold Pressed.<br/>Straight From Source.
        </h1>
        <p style="font-size:var(--ts-lg);opacity:0.85;line-height:1.6;margin-bottom:var(--sp-8);max-width:480px">
          Hand-picked cold pressed oils and masala powders made the traditional way.
          No preservatives. No additives. Just pure goodness.
        </p>
        <div style="display:flex;gap:var(--sp-3);flex-wrap:wrap">
          <a href="#/products?category=oils" class="btn btn-lg" style="background:white;color:var(--clr-primary);border-color:white;font-weight:700">
            🫙 Shop Oils
          </a>
          <a href="#/products?category=masala" class="btn btn-lg btn-secondary" style="color:white;border-color:rgba(255,255,255,0.6)">
            🌶 Shop Masalas
          </a>
        </div>
      </div>
    </div>
  </section>

  <!-- ── TRUST STRIP ──────────────────────────────────────── -->
  <div class="trust-strip">
    <div class="container">
      <div class="trust-strip-inner">
        <div class="trust-item">✓ No Additives</div>
        <div class="trust-item">✓ Cold Pressed</div>
        <div class="trust-item">✓ No Preservatives</div>
        <div class="trust-item">✓ Fast Delivery</div>
        <div class="trust-item">✓ 100% Pure</div>
      </div>
    </div>
  </div>

  <!-- ── CATEGORIES ───────────────────────────────────────── -->
  <section class="section-sm">
    <div class="container">
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:var(--sp-4)">

        ${[
          { href:'#/products?category=oils',   bg:'linear-gradient(135deg,#E6F5EC,#C3E8D1)', icon:'🫙', h3color:'var(--clr-primary)',  title:'Cold Pressed Oils',   desc:'Groundnut, Sesame, Coconut &amp; more — extracted without heat.',      cta:'Shop Oils',    ctaclr:'var(--clr-primary)' },
          { href:'#/products?category=masala', bg:'linear-gradient(135deg,#FFF8E1,#FFECB3)', icon:'🌶', h3color:'#E65100',             title:'Masala Powders',      desc:'Sambar, Chilli, Coriander &amp; more — ground fresh from whole spices.', cta:'Shop Masalas', ctaclr:'var(--clr-accent)' },
          { href:'#/products?category=flours', bg:'linear-gradient(135deg,#FFF3E0,#FFE0B2)', icon:'🌾', h3color:'#BF360C',             title:'Flours &amp; Grains', desc:'Wheat, Ragi, Kambu &amp; millets — stone ground traditionally.',         cta:'Shop Flours',  ctaclr:'#BF360C' },
          { href:'#/products?category=health', bg:'linear-gradient(135deg,#F2FAE3,#DCEDC8)', icon:'💚', h3color:'var(--clr-secondary)', title:'Health Mixes',        desc:'Nutritious mixes for everyday wellness — ready to mix and drink.',        cta:'Shop Health',  ctaclr:'var(--clr-secondary)' },
        ].map(c => `
        <a href="${c.href}" style="
          display:block;
          background:${c.bg};
          border-radius:var(--r-xl);
          padding:var(--sp-8) var(--sp-6);
          text-decoration:none;
          transition:transform var(--t-normal),box-shadow var(--t-normal);
        " onmouseover="this.style.transform='translateY(-4px)';this.style.boxShadow='var(--shadow-md)'"
           onmouseout="this.style.transform='';this.style.boxShadow=''">
          <div style="font-size:2.5rem;margin-bottom:var(--sp-3)">${c.icon}</div>
          <h3 style="color:${c.h3color};margin-bottom:var(--sp-2);font-size:var(--ts-lg)">${c.title}</h3>
          <p style="color:var(--clr-text-b);font-size:var(--ts-sm);line-height:1.5">${c.desc}</p>
          <span style="display:inline-block;margin-top:var(--sp-4);color:${c.ctaclr};font-weight:600;font-size:var(--ts-sm)">${c.cta} →</span>
        </a>`).join('')}

      </div>
    </div>
  </section>

  <!-- ── FEATURED PRODUCTS ────────────────────────────────── -->
  <section class="section">
    <div class="container">
      <div class="section-header">
        <h2 class="section-title">Featured Products</h2>
        <a href="#/products" class="section-link">View All →</a>
      </div>
      <div class="product-grid" id="featuredGrid">
        <!-- Populated by JS -->
      </div>
    </div>
  </section>

  <!-- ── WHY SASOORI ──────────────────────────────────────── -->
  <section class="section-sm" style="background:var(--clr-surface)">
    <div class="container">
      <h2 class="section-title" style="text-align:center;margin-bottom:var(--sp-10)">Why Choose Sasoori?</h2>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:var(--sp-6)">
        ${[
          { icon:'🌿', title:'100% Natural',     desc:'No artificial colours, flavours or preservatives. Ever.' },
          { icon:'❄️', title:'Cold Pressed',      desc:'Extracted without heat — all vitamins and nutrients intact.' },
          { icon:'🏡', title:'Traditional Recipe', desc:'Made using stone-grinding methods passed down generations.' },
          { icon:'🚚', title:'Fast Delivery',      desc:'Delivered fresh to your door in 3–5 working days.' },
        ].map(i => `
          <div style="text-align:center;padding:var(--sp-6)">
            <div style="font-size:2.5rem;margin-bottom:var(--sp-3)">${i.icon}</div>
            <h4 style="margin-bottom:var(--sp-2);color:var(--clr-text-h)">${i.title}</h4>
            <p style="font-size:var(--ts-sm);color:var(--clr-text-m);line-height:1.6">${i.desc}</p>
          </div>
        `).join('')}
      </div>
    </div>
  </section>

  <!-- ── TESTIMONIALS ─────────────────────────────────────── -->
  <section class="section">
    <div class="container">
      <h2 class="section-title" style="text-align:center;margin-bottom:var(--sp-10)">What Our Customers Say</h2>
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:var(--sp-6)">
        ${[
          { name:'Priya Sharma',    loc:'Chennai',   rating:5, text:'The groundnut oil is absolutely pure. You can smell the difference immediately. My family loves it!' },
          { name:'Ravi Kumar',      loc:'Bangalore', rating:5, text:'Been using Sasoori sesame oil for 6 months. Best cold pressed oil I\'ve found online. Fast delivery too!' },
          { name:'Ananya Nair',     loc:'Kochi',     rating:5, text:'The sambar powder is the real deal. Tastes exactly like what my grandmother used to make. Highly recommend.' },
        ].map(t => `
          <div style="
            background:var(--clr-surface);
            border-radius:var(--r-xl);
            padding:var(--sp-6);
            box-shadow:var(--shadow-sm);
            border:1px solid var(--clr-border);
          ">
            <div style="color:var(--clr-accent);font-size:var(--ts-lg);margin-bottom:var(--sp-3)">
              ${'★'.repeat(t.rating)}
            </div>
            <p style="font-size:var(--ts-sm);color:var(--clr-text-b);line-height:1.7;font-style:italic;margin-bottom:var(--sp-4)">
              "${t.text}"
            </p>
            <div style="display:flex;align-items:center;gap:var(--sp-3)">
              <div style="
                width:40px;height:40px;border-radius:50%;
                background:var(--clr-primary-light);
                display:flex;align-items:center;justify-content:center;
                font-weight:700;color:var(--clr-primary);font-size:var(--ts-base)
              ">${t.name[0]}</div>
              <div>
                <div style="font-weight:600;font-size:var(--ts-sm);color:var(--clr-text-h)">${t.name}</div>
                <div style="font-size:var(--ts-xs);color:var(--clr-text-m)">${t.loc}</div>
              </div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>
  </section>
`;

export default page;
