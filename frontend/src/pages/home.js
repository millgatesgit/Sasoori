/**
 * SASOORI — Home Page
 * Route: #/
 */

import { productCardHTML, attachCardEvents, skeletonCards } from '../components/productCard.js';
import { getProducts } from '../api/productApi.js';

const page = {
  meta: () => ({
    title: 'Pure Cold Pressed Oils & Traditional Masala',
    description: 'Sasoori — 100% natural cold pressed oils and masala powders from Tamil Nadu. No chemicals. No preservatives. Direct from source.',
  }),

  async render(container) {
    container.innerHTML = HOME_TEMPLATE;
  },

  async onMount() {
    await loadBestSellers();
    initLazyLoad();
  },

  onDestroy() {},
};

// ── Data loaders ────────────────────────────────────────────────
async function loadBestSellers() {
  const grid = document.getElementById('featuredGrid');
  if (!grid) return;

  grid.innerHTML = skeletonCards(8);

  try {
    const result   = await getProducts({ size: 8 });
    const products = result.products || result;

    if (!products.length) {
      grid.innerHTML = `<p class="text-muted" style="grid-column:1/-1;text-align:center;padding:var(--sp-8)">
        No products yet — check back soon!
      </p>`;
      return;
    }

    grid.innerHTML = products.map(productCardHTML).join('');
    attachCardEvents(grid);
  } catch {
    grid.innerHTML = `<p class="text-muted" style="grid-column:1/-1;text-align:center;padding:var(--sp-8)">
      Could not load products. Please refresh.
    </p>`;
  }
}

function initLazyLoad() {
  if (!('IntersectionObserver' in window)) {
    // Fallback: load all immediately
    document.querySelectorAll('img[data-src]').forEach(img => {
      img.src = img.dataset.src;
    });
    return;
  }
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const img = entry.target;
        img.src = img.dataset.src;
        img.classList.add('loaded');
        observer.unobserve(img);
      }
    });
  }, { rootMargin: '250px' });

  document.querySelectorAll('img[data-src]').forEach(img => observer.observe(img));
}

// ── Category data ───────────────────────────────────────────────
const CATEGORIES = [
  {
    href:     '#/products?category=oils',
    img:      'assets/images/products/groundnut-oil-1l.jpg',
    badge:    'Best Seller',
    badgeClr: '#00923F',
    ta:       'செக்கு எண்ணெய்',
    title:    'Cold Pressed Oils',
    desc:     'Groundnut, Sesame, Coconut &amp; more',
    price:    'From ₹150',
    cta:      'Shop Oils',
    accent:   '#00923F',
  },
  {
    href:     '#/products?category=masala',
    img:      'assets/images/products/sambar-powder-100g.jpg',
    badge:    'Most Popular',
    badgeClr: '#E65100',
    ta:       'மசாலா தூள்',
    title:    'Masala Powders',
    desc:     'Sambar, Chilli, Coriander &amp; more',
    price:    'From ₹80',
    cta:      'Shop Masalas',
    accent:   '#E65100',
  },
  {
    href:     '#/products?category=flours',
    img:      'assets/images/products/wheat-flour-1kg.jpg',
    badge:    null,
    ta:       'மாவு &amp; தானியம்',
    title:    'Flours &amp; Grains',
    desc:     'Wheat, Ragi, Kambu &amp; millets',
    price:    'From ₹120',
    cta:      'Shop Flours',
    accent:   '#BF360C',
  },
  {
    href:     '#/products?category=health',
    img:      'assets/images/products/health-mix-500g.jpg',
    badge:    null,
    ta:       'ஆரோக்கிய கலவை',
    title:    'Health Mixes',
    desc:     'Nutritious mixes for everyday wellness',
    price:    'From ₹199',
    cta:      'Shop Health',
    accent:   '#558B2F',
  },
];

// ── Why Choose Us items ─────────────────────────────────────────
const WHY_ITEMS = [
  { icon: '🚫', ta: 'கலப்படம் இல்லை',           en: 'No Adulteration'         },
  { icon: '🌶', ta: 'புதிதாக அரைத்தது',           en: 'Freshly Ground Masalas'  },
  { icon: '🌾', ta: 'நேரடியாக விவசாயிகளிடம்',    en: 'Direct from Farmers'     },
  { icon: '🧪', ta: 'பாதுகாப்பு பொருட்கள் இல்லை', en: 'No Preservatives'        },
  { icon: '🏺', ta: 'பாரம்பரிய முறை',             en: 'Traditional Method'      },
];

// ── Education items ─────────────────────────────────────────────
const EDU_ITEMS = [
  { icon: '🌡️', title: 'No Heat Used',          desc: 'Extracted below 40°C — vitamins, antioxidants and omega acids are fully preserved.' },
  { icon: '🧴', title: 'vs Refined Oil',         desc: 'Refined oils are bleached and chemically treated. Cold pressed = none of that.' },
  { icon: '❤️', title: 'Heart Healthy',           desc: 'Rich in natural Vitamin E, reduces bad cholesterol and supports cardiovascular health.' },
  { icon: '🏺', title: '700 Years of Tradition',  desc: 'Wooden chekku pressing has been used in Tamil Nadu for centuries — for good reason.' },
];

// ── Testimonials ────────────────────────────────────────────────
const TESTIMONIALS = [
  {
    init: 'P', name: 'Priya R.',     loc: 'Chennai',
    rating: 5,
    ta: 'எண்ணெய் ரொம்ப நல்லா இருக்கு. Pure taste!',
    en: 'The groundnut oil is absolutely pure. You can smell the difference immediately.',
  },
  {
    init: 'R', name: 'Ravi Kumar',   loc: 'Coimbatore',
    rating: 5,
    ta: '6 மாதமா உபயோகிக்கிறேன். சுத்தமான எண்ணெய்.',
    en: 'Been using Sasoori sesame oil for 6 months. Best cold pressed oil I\'ve found.',
  },
  {
    init: 'A', name: 'Ananya S.',    loc: 'Madurai',
    rating: 5,
    ta: 'அம்மா செய்த சாம்பார் மாதிரியே ருசியா இருக்கு!',
    en: 'The sambar powder tastes exactly like what my grandmother used to make.',
  },
  {
    init: 'S', name: 'Suresh M.',    loc: 'Tirunelveli',
    rating: 5,
    ta: 'நேரடியா விவசாயிகளிடம் வாங்குவது போல் நம்பிக்கை இருக்கு.',
    en: 'Feels like buying directly from the farmer. Very trustworthy brand.',
  },
];

// ── Template ────────────────────────────────────────────────────
const HOME_TEMPLATE = `

  <!-- ══ HERO ═══════════════════════════════════════════════ -->
  <section class="hero-v2">
    <div class="hero-v2-inner container">

      <!-- LEFT 60% — Text & CTAs -->
      <div class="hero-v2-text">
        <span class="hero-tag">🌿 பாரம்பரிய முறை · Traditional Extraction</span>

        <h1 class="hero-v2-h1">
          செக்கு எண்ணெய்.<br/>தூய்மை. நம்பிக்கை.
        </h1>

        <p class="hero-v2-tagline">
          உங்கள் குடும்பத்துக்கு நேராக — பாரம்பரிய முறையில் தயாரிப்பு
        </p>

        <p class="hero-v2-desc">
          No chemicals. Traditional extraction. Direct from source.
        </p>

        <div class="hero-badges">
          <span class="hero-badge">✓ 100% Cold Pressed</span>
          <span class="hero-badge">✓ No Chemicals</span>
          <span class="hero-badge">✓ Made in Tamil Nadu</span>
        </div>

        <div class="hero-v2-ctas">
          <a href="#/products?category=oils"   class="btn btn-lg hero-cta-primary">🫙 Shop Oils</a>
          <a href="#/products?category=masala" class="btn btn-lg hero-cta-secondary">🌶 Shop Masalas</a>
        </div>

        <p class="hero-trust-line">
          ⭐ Trusted by 1000+ families across Tamil Nadu
        </p>
      </div>

      <!-- RIGHT 40% — Structured product showcase -->
      <div class="hero-v2-visual">
        <div class="hero-img-main-card">
          <img src="assets/images/products/groundnut-oil-1l.jpg"
               alt="Cold pressed groundnut oil"
               loading="eager" />
        </div>
        <div class="hero-img-row">
          <div class="hero-img-small-card">
            <img src="assets/images/products/sesame-oil-500ml.jpg"
                 alt="Cold pressed sesame oil"
                 loading="eager" />
            <span class="hero-img-label">Sesame Oil</span>
          </div>
          <div class="hero-img-small-card">
            <img src="assets/images/products/sambar-powder-100g.jpg"
                 alt="Fresh ground sambar masala"
                 loading="eager" />
            <span class="hero-img-label">Sambar Masala</span>
          </div>
        </div>
      </div>

    </div>
  </section>

  <!-- ══ ASSURANCE BAR ═══════════════════════════════════════ -->
  <div class="assurance-bar">
    <div class="container">
      <div class="assurance-bar-inner">
        <div class="assurance-item">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><rect x="1" y="3" width="15" height="13" rx="1"/><path d="M16 8h4l3 5v3h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
          <span>Fast Delivery — Tamil Nadu</span>
        </div>
        <div class="assurance-sep"></div>
        <div class="assurance-item">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>
          <span>Cash on Delivery</span>
        </div>
        <div class="assurance-sep"></div>
        <div class="assurance-item">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          <span>Easy Returns</span>
        </div>
        <div class="assurance-sep"></div>
        <div class="assurance-item">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
          <span>Secure Payment</span>
        </div>
      </div>
    </div>
  </div>

  <!-- ══ WHY CHOOSE US ═══════════════════════════════════════ -->
  <section class="section why-section">
    <div class="container">
      <div class="why-layout">

        <div class="why-visual">
          <div class="why-img-wrap">
            <img data-src="assets/images/products/sesame-oil-1l.jpg"
                 src=""
                 alt="Traditional chekku sesame oil extraction"
                 class="why-img lazy" />
            <div class="why-img-badge">
              செக்கு எண்ணெய்
              <small>Wooden Chekku Extraction</small>
            </div>
          </div>
        </div>

        <div class="why-content">
          <p class="why-tag">ஏன் சாசூரி? · Why Sasoori?</p>
          <h2 class="section-title why-title">Pure From Source.<br/>Nothing Added.</h2>
          <ul class="why-list">
            ${WHY_ITEMS.map(w => `
              <li class="why-item">
                <span class="why-icon">${w.icon}</span>
                <div>
                  <span class="why-ta">${w.ta}</span>
                  <span class="why-en">${w.en}</span>
                </div>
              </li>
            `).join('')}
          </ul>
          <a href="#/products" class="btn btn-primary">எல்லா தயாரிப்புகளும் → View All</a>
        </div>

      </div>
    </div>
  </section>

  <!-- ══ CATEGORY CARDS ══════════════════════════════════════ -->
  <section class="section-sm cat-section">
    <div class="container">
      <div class="section-header">
        <h2 class="section-title">Shop by Category</h2>
        <a href="#/products" class="section-link">View All →</a>
      </div>
      <div class="cat-grid">
        ${CATEGORIES.map(c => `
          <a href="${c.href}" class="cat-card">
            ${c.badge ? `<div class="cat-badge" style="background:${c.badgeClr}">${c.badge}</div>` : ''}
            <div class="cat-card-img-wrap">
              <img data-src="${c.img}" src="" alt="${c.title}" class="cat-card-img lazy" />
            </div>
            <div class="cat-card-body">
              <div class="cat-card-ta">${c.ta}</div>
              <h3 class="cat-card-title" style="color:${c.accent}">${c.title}</h3>
              <p class="cat-card-desc">${c.desc}</p>
              <div class="cat-card-footer">
                <span class="cat-card-price">${c.price}</span>
                <span class="cat-card-cta" style="color:${c.accent}">${c.cta} →</span>
              </div>
            </div>
          </a>
        `).join('')}
      </div>
    </div>
  </section>

  <!-- ══ BEST SELLERS ════════════════════════════════════════ -->
  <section class="section best-section" style="background:var(--clr-surface)">
    <div class="container">
      <div class="section-header">
        <div>
          <h2 class="section-title">Most Bought in Tamil Nadu</h2>
          <p class="section-subtitle">தமிழகத்தில் அதிகம் வாங்கப்படும் தயாரிப்புகள்</p>
        </div>
        <a href="#/products" class="section-link" style="flex-shrink:0">View All →</a>
      </div>
      <div class="product-grid" id="featuredGrid">
        <!-- Populated by JS -->
      </div>
    </div>
  </section>

  <!-- ══ WHY COLD PRESSED ════════════════════════════════════ -->
  <section class="section-sm edu-section">
    <div class="container">
      <div style="text-align:center;margin-bottom:var(--sp-10)">
        <p class="why-tag" style="justify-content:center">அறிவியல் · Science</p>
        <h2 class="section-title">Why Cold Pressed Oil?</h2>
        <p style="color:var(--clr-text-m);max-width:460px;margin:var(--sp-2) auto 0;font-size:var(--ts-sm);line-height:1.7">
          செக்கு எண்ணெய் vs சுத்திகரிக்கப்பட்ட எண்ணெய் — the difference that matters.
        </p>
      </div>
      <div class="edu-grid">
        ${EDU_ITEMS.map(e => `
          <div class="edu-card">
            <div class="edu-icon">${e.icon}</div>
            <h4 class="edu-title">${e.title}</h4>
            <p class="edu-desc">${e.desc}</p>
          </div>
        `).join('')}
      </div>
    </div>
  </section>

  <!-- ══ TESTIMONIALS ════════════════════════════════════════ -->
  <section class="section testi-section" style="background:var(--clr-surface)">
    <div class="container">
      <div style="text-align:center;margin-bottom:var(--sp-10)">
        <p class="why-tag" style="justify-content:center">நம்பகமான · Trusted</p>
        <h2 class="section-title">Trusted by 1000+ Tamil Families</h2>
      </div>
      <div class="testi-grid">
        ${TESTIMONIALS.map(t => `
          <div class="testi-card">
            <div class="testi-stars">${'★'.repeat(t.rating)}</div>
            <p class="testi-ta">"${t.ta}"</p>
            <p class="testi-en">${t.en}</p>
            <div class="testi-author">
              <div class="testi-avatar">${t.init}</div>
              <div>
                <div class="testi-name">${t.name}</div>
                <div class="testi-loc">📍 ${t.loc}</div>
              </div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>
  </section>
`;

export default page;
