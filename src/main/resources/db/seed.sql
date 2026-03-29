-- ============================================================
-- SASOORI — Seed Data
-- Run after schema.sql:
--   psql -U sasoori -d sasoori_db -f seed.sql
-- ============================================================

-- ── Categories ───────────────────────────────────────────────
INSERT INTO categories (id, name, slug, description, image_url, sort_order) VALUES
  (1, 'Cold Pressed Oils', 'oils',   'Pure cold pressed oils extracted the traditional way',   '/assets/images/products/sesame-oil-500ml.jpg',   1),
  (2, 'Masala Powders',    'masala', 'Freshly ground spice blends with no additives',          '/assets/images/products/sambar-powder-500g.jpg', 2),
  (3, 'Flours & Grains',   'flours', 'Stone ground flours from traditional varieties',         '/assets/images/products/wheat-flour-1kg.jpg',    3),
  (4, 'Health Mixes',      'health', 'Nutritious mixes for everyday wellness',                 '/assets/images/products/health-mix-500g.jpg',    4)
ON CONFLICT (slug) DO NOTHING;

-- Reset serial to avoid conflict with explicit IDs
SELECT setval('categories_id_seq', 4);

-- ── Products ─────────────────────────────────────────────────
INSERT INTO products (category_id, name, slug, sku, description, ingredients, weight_grams, price_paise, mrp_paise, stock_qty, images, tags, is_active, is_featured) VALUES

-- OILS
(1, 'Sesame Oil', 'sesame-oil-500ml', 'SAR-OIL-SES-500',
 'Cold pressed sesame oil extracted from premium quality sesame seeds. Rich in antioxidants, vitamin E and healthy fats. Perfect for cooking, tempering and skin care.',
 '100% Cold Pressed Sesame Seeds', 500, 31900, 35000, 50,
 '["/assets/images/products/sesame-oil-500ml.jpg"]',
 ARRAY['oils','featured','bestseller'], TRUE, TRUE),

(1, 'Sesame Oil', 'sesame-oil-1l', 'SAR-OIL-SES-1000',
 'Cold pressed sesame oil extracted from premium quality sesame seeds. Rich in antioxidants, vitamin E and healthy fats. Perfect for cooking, tempering and skin care.',
 '100% Cold Pressed Sesame Seeds', 1000, 59900, 65000, 40,
 '["/assets/images/products/sesame-oil-1l.jpg"]',
 ARRAY['oils','bestseller'], TRUE, FALSE),

(1, 'Sesame Oil', 'sesame-oil-100ml', 'SAR-OIL-SES-100',
 'Cold pressed sesame oil in a convenient 100ml bottle. Great for trials and small households.',
 '100% Cold Pressed Sesame Seeds', 100, 8500, 9900, 60,
 '["/assets/images/products/sesame-oil-100ml.jpg"]',
 ARRAY['oils','new'], TRUE, FALSE),

(1, 'Coconut Oil', 'coconut-oil-500ml', 'SAR-OIL-COC-500',
 'Virgin cold pressed coconut oil with natural aroma and taste. Ideal for cooking, hair and skin care. Extracted from fresh coconuts without any chemical processing.',
 '100% Cold Pressed Coconut', 500, 28900, 32000, 45,
 '["/assets/images/products/coconut-oil-500ml.jpg"]',
 ARRAY['oils','featured'], TRUE, TRUE),

(1, 'Coconut Oil', 'coconut-oil-1l', 'SAR-OIL-COC-1000',
 'Virgin cold pressed coconut oil with natural aroma and taste. Ideal for cooking, hair and skin care. Extracted from fresh coconuts without any chemical processing.',
 '100% Cold Pressed Coconut', 1000, 54900, 60000, 35,
 '["/assets/images/products/coconut-oil-1l.jpg"]',
 ARRAY['oils'], TRUE, FALSE),

(1, 'Groundnut Oil', 'groundnut-oil-1l', 'SAR-OIL-GND-1000',
 'Cold pressed groundnut oil with a rich, nutty flavour. High in monounsaturated fats. Ideal for deep frying, sautéing and everyday cooking.',
 '100% Cold Pressed Groundnuts', 1000, 44900, 50000, 55,
 '["/assets/images/products/groundnut-oil-1l.jpg"]',
 ARRAY['oils','featured','bestseller'], TRUE, TRUE),

-- MASALAS
(2, 'Sambar Powder', 'sambar-powder-100g', 'SAR-MAS-SAM-100',
 'Authentic South Indian sambar powder blended from freshly ground spices. No artificial colours, flavours or preservatives. Brings the authentic taste of home-cooked sambar.',
 'Coriander, Red Chilli, Cumin, Black Pepper, Curry Leaves, Chana Dal, Urad Dal, Turmeric', 100, 8000, 9000, 80,
 '["/assets/images/products/sambar-powder-100g.jpg"]',
 ARRAY['masala','bestseller','featured'], TRUE, TRUE),

(2, 'Sambar Powder', 'sambar-powder-200g', 'SAR-MAS-SAM-200',
 'Authentic South Indian sambar powder blended from freshly ground spices. No artificial colours, flavours or preservatives.',
 'Coriander, Red Chilli, Cumin, Black Pepper, Curry Leaves, Chana Dal, Urad Dal, Turmeric', 200, 15000, 17000, 70,
 '["/assets/images/products/sambar-powder-200g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

(2, 'Sambar Powder', 'sambar-powder-500g', 'SAR-MAS-SAM-500',
 'Authentic South Indian sambar powder blended from freshly ground spices. No artificial colours, flavours or preservatives.',
 'Coriander, Red Chilli, Cumin, Black Pepper, Curry Leaves, Chana Dal, Urad Dal, Turmeric', 500, 32000, 36000, 60,
 '["/assets/images/products/sambar-powder-500g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

(2, 'Chilli Powder', 'chilli-powder-100g', 'SAR-MAS-CHI-100',
 'Pure red chilli powder made from sun-dried Byadagi and Guntur chillies. Vibrant colour, medium heat, no additives.',
 '100% Sun-dried Red Chillies', 100, 7000, 8000, 90,
 '["/assets/images/products/chilli-powder-100g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

(2, 'Chilli Powder', 'chilli-powder-500g', 'SAR-MAS-CHI-500',
 'Pure red chilli powder made from sun-dried Byadagi and Guntur chillies. Vibrant colour, medium heat, no additives.',
 '100% Sun-dried Red Chillies', 500, 22000, 25000, 75,
 '["/assets/images/products/chilli-powder-500g.jpg"]',
 ARRAY['masala','featured'], TRUE, TRUE),

(2, 'Coriander Powder', 'coriander-powder-500g', 'SAR-MAS-COR-500',
 'Freshly ground coriander powder from premium coriander seeds. Mild, citrusy aroma that enhances curries, gravies and rice dishes.',
 '100% Coriander Seeds', 500, 18000, 22000, 85,
 '["/assets/images/products/coriander-powder-500g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

(2, 'Turmeric Powder', 'turmeric-powder-200g', 'SAR-MAS-TUR-200',
 'Pure Erode turmeric powder with high curcumin content. Vibrant golden colour, earthy aroma. No artificial colour or starch added.',
 '100% Turmeric', 200, 8000, 9000, 100,
 '["/assets/images/products/turmeric-powder-200g.jpg"]',
 ARRAY['masala','bestseller'], TRUE, FALSE),

(2, 'Curry Masala', 'curry-masala-200g', 'SAR-MAS-CUR-200',
 'A versatile South Indian curry masala blend. Perfect for vegetable curries, chicken curries and gravies. Freshly ground, no MSG or preservatives.',
 'Coriander, Cumin, Black Pepper, Red Chilli, Cardamom, Cinnamon, Clove, Turmeric', 200, 12000, 14000, 65,
 '["/assets/images/products/curry-masala-200g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

(2, 'Curry Masala', 'curry-masala-500g', 'SAR-MAS-CUR-500',
 'A versatile South Indian curry masala blend. Perfect for vegetable curries, chicken curries and gravies. Freshly ground, no MSG or preservatives.',
 'Coriander, Cumin, Black Pepper, Red Chilli, Cardamom, Cinnamon, Clove, Turmeric', 500, 28000, 32000, 55,
 '["/assets/images/products/curry-masala-500g.jpg"]',
 ARRAY['masala','featured'], TRUE, TRUE),

(2, 'Kara Masala', 'kara-masala-100g', 'SAR-MAS-KAR-100',
 'Traditional South Indian kara (spicy) masala blend. Ideal for kara kuzhambu, spicy gravies and rice dishes.',
 'Red Chilli, Coriander, Cumin, Black Pepper, Curry Leaves, Tamarind', 100, 9000, 10500, 70,
 '["/assets/images/products/kara-masala-100g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

(2, 'Chicken Masala', 'chicken-masala-100g', 'SAR-MAS-CHK-100',
 'Freshly ground chicken masala with a perfect blend of aromatic spices. Makes authentic South Indian chicken curry without any effort.',
 'Coriander, Red Chilli, Cumin, Black Pepper, Ginger, Garlic, Cardamom, Cinnamon, Clove, Star Anise', 100, 11000, 13000, 60,
 '["/assets/images/products/chicken-masala-100g.jpg"]',
 ARRAY['masala','new'], TRUE, FALSE),

(2, 'Dal Powder', 'dal-powder-100g', 'SAR-MAS-DAL-100',
 'A traditional South Indian dal powder to mix with rice and ghee. Made with roasted lentils and spices.',
 'Roasted Chana Dal, Red Chilli, Cumin, Black Pepper, Curry Leaves, Asafoetida, Salt', 100, 8000, 9000, 75,
 '["/assets/images/products/dal-powder-100g.jpg"]',
 ARRAY['masala'], TRUE, FALSE),

-- FLOURS & GRAINS
(3, 'Wheat Flour', 'wheat-flour-500g', 'SAR-FLR-WHT-500',
 'Stone ground whole wheat flour from traditional wheat varieties. Retains natural bran and germ. Makes soft, flavourful rotis and chapatis.',
 '100% Whole Wheat (Stone Ground)', 500, 4500, 5500, 120,
 '["/assets/images/products/wheat-flour-500g.jpg"]',
 ARRAY['flours','bestseller'], TRUE, FALSE),

(3, 'Wheat Flour', 'wheat-flour-1kg', 'SAR-FLR-WHT-1000',
 'Stone ground whole wheat flour from traditional wheat varieties. Retains natural bran and germ. Makes soft, flavourful rotis and chapatis.',
 '100% Whole Wheat (Stone Ground)', 1000, 8500, 10000, 100,
 '["/assets/images/products/wheat-flour-1kg.jpg"]',
 ARRAY['flours','featured'], TRUE, TRUE),

(3, 'Ragi Flour', 'ragi-flour-500g', 'SAR-FLR-RAG-500',
 'Stone ground finger millet (ragi) flour. Rich in calcium, iron and dietary fibre. Ideal for ragi dosa, ragi mudde and healthy porridge.',
 '100% Finger Millet (Ragi), Stone Ground', 500, 5500, 6500, 90,
 '["/assets/images/products/ragi-flour-500g.jpg"]',
 ARRAY['flours','healthy','new'], TRUE, FALSE),

(3, 'Gram Flour', 'gram-flour-500g', 'SAR-FLR-GRM-500',
 'Freshly ground chickpea (besan) flour. High in protein and fibre. Great for batter, pakoras, sweets and as a binding agent.',
 '100% Chickpeas (Chana), Stone Ground', 500, 7000, 8000, 80,
 '["/assets/images/products/gram-flour-500g.jpg"]',
 ARRAY['flours'], TRUE, FALSE),

(3, 'Pearl Millet Flour', 'pearl-millet-flour-500g', 'SAR-FLR-PML-500',
 'Stone ground pearl millet (kambu) flour. High in iron and protein. Traditional flour for making bajra roti and porridge.',
 '100% Pearl Millet (Kambu), Stone Ground', 500, 6000, 7000, 70,
 '["/assets/images/products/pearl-millet-flour-500g.jpg"]',
 ARRAY['flours','healthy'], TRUE, FALSE),

(3, 'Sorghum Flour', 'sorghum-flour-500g', 'SAR-FLR-SOR-500',
 'Stone ground sorghum (cholam) flour. Gluten-free, high in nutrients. Great for making jowar roti, dosa and porridge.',
 '100% Sorghum (Cholam), Stone Ground', 500, 6500, 7500, 65,
 '["/assets/images/products/sorghum-flour-500g.jpg"]',
 ARRAY['flours','healthy','glutenfree'], TRUE, FALSE),

(3, 'Small Millet Mix', 'small-millet-mix-500g', 'SAR-FLR-SMM-500',
 'A nourishing blend of small millets — thinai, varagu, kuthiraivali and more. Stone ground for maximum nutrition.',
 'Foxtail Millet, Kodo Millet, Barnyard Millet, Little Millet (Stone Ground)', 500, 9000, 10500, 55,
 '["/assets/images/products/small-millet-mix-500g.jpg"]',
 ARRAY['flours','healthy','new'], TRUE, FALSE),

(3, 'Urad Dal Mix', 'urad-dal-mix-250g', 'SAR-FLR-URD-250',
 'Ready-to-use urad dal flour mix for making soft idlis and crispy dosas. Traditional recipe, no additives.',
 'Urad Dal, Rice (Stone Ground)', 250, 8000, 9000, 60,
 '["/assets/images/products/urad-dal-mix-250g.jpg"]',
 ARRAY['flours'], TRUE, FALSE),

-- HEALTH MIXES
(4, 'Saroori Health Mix', 'health-mix-500g', 'SAR-HLT-MIX-500',
 'A wholesome blend of grains, millets and legumes ground together. Rich in protein, fibre and essential nutrients. Great as a breakfast porridge or energy drink.',
 'Wheat, Ragi, Cholam, Kambu, Thinai, Roasted Gram, Cardamom, Dry Ginger', 500, 12000, 14000, 75,
 '["/assets/images/products/health-mix-500g.jpg"]',
 ARRAY['health','featured','bestseller'], TRUE, TRUE),

(4, 'Cane Sugar', 'cane-sugar-500g', 'SAR-HLT-CAN-500',
 'Unrefined nattu sakkarai (country sugar) from natural sugarcane juice. Retains molasses for natural sweetness and minerals. No bleaching or chemical processing.',
 '100% Natural Sugarcane', 500, 6000, 7000, 90,
 '["/assets/images/products/cane-sugar-500g.jpg"]',
 ARRAY['health','new'], TRUE, FALSE),

(4, 'Groundnuts', 'groundnuts-500g', 'SAR-HLT-GND-500',
 'Raw, unroasted groundnuts (peanuts) sourced directly from farmers. High in protein, healthy fats and vitamin B3. No salt or preservatives added.',
 '100% Natural Groundnuts', 500, 8500, 10000, 80,
 '["/assets/images/products/groundnuts-500g.jpg"]',
 ARRAY['health'], TRUE, FALSE)

ON CONFLICT (slug) DO NOTHING;
