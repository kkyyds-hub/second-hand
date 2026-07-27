# P7-UI-A Commerce Visual Foundation Design

## Scope and decision

Build a lightweight, discovery-first commerce visual system for the user app. The approved direction borrows the information hierarchy of mature marketplaces without copying their brands, slogans, iconography, layouts, or promotional mechanics.

The user app will use a warm orange-red accent, warm off-white page surfaces, restrained borders and shadows, and product-forward cards. It will continue to use the existing APIs, routes, user/session state, cart store, request isolation, market filters, pagination, favorites, checkout, seller-shop data, and product detail actions.

The change is limited to the user app files authorized by the task. No backend, DTO, database, admin UI, deployment, auth, or transaction behavior will change.

## Information architecture

### Desktop

The global shell becomes four visual bands:

1. A slim utility row for low-priority context and account actions.
2. A brand-and-search row. Search is the dominant entry and submits only to the existing `/market?keyword=` query flow.
3. A concise primary navigation row containing home, market, and seller access where the current user is a seller.
4. A content region and restrained footer.

Favorites, cart, buyer orders, and the user account menu stay in the top-right action cluster. The cart badge stays there. They are intentionally not repeated in page content.

### Mobile

The mobile header keeps an always available cart action and badge. The existing bottom navigation remains for touch reachability and safe-area handling, but it no longer includes a cart item. Its remaining items are home, market, seller publish or favorites, orders, and account. The header cart is the sole mobile cart route.

### Home

The homepage becomes one discovery surface:

1. Hero with the new generated image, search, market CTA, and seller-only publish CTA.
2. Four non-duplicative shortcuts: market, favorites, buyer orders, and seller center for sellers. Cart is excluded because it is globally available in the header.
3. One real-data “recently listed” product stream with existing loading skeleton, retryable error, empty state, and market link.
4. A compact trust/capability strip limited to actual supported functionality.
5. Seller publishing invitation only for sellers.

No fake categories, campaign modules, discounts, coupons, sales counts, shipping claims, official certification, countdowns, or artificial product records will be introduced.

## Visual system

`style.css` will provide semantic token utilities for:

- brand, brand-soft, accent, price, success, warning, danger, canvas, surface, border, primary text, and secondary text;
- small, medium, and large radius; normal, hover, and overlay shadows; desktop container width; standard content spacing; button and input height; image placeholder color; and duration/easing;
- upgraded shared shell, navigation, form, button, state, and product-card primitives.

Orange-red is limited to identity, primary actions, active navigation, cart count, and price. Neutral content stays warm gray and white so the app does not read as a recolored blue template or a promotion-heavy red site. Focus indicators use the brand color and all nonessential motion is disabled under `prefers-reduced-motion`.

## Shared product card

The existing `MarketplaceProductCard.vue` is the shared card foundation and gains a type-safe variant API:

- `standard`: interactive on-sale card for home, market results, and related on-sale products;
- `compact`: denser related-product presentation where space requires it;
- `sold`: non-interactive visual treatment for seller-shop sold products.

The component accepts a small normalized display contract instead of relying on fields unavailable from seller-shop data. A mapping inside `SellerShopPage.vue` creates the required card data without changing API types or endpoints. The card remains non-navigable without a positive product ID, lazy-loads non-hero images, shows an image fallback after a load failure, truncates titles to two lines, exposes a visible keyboard focus state, and may show the existing favorite control only when the calling page provides it. Sold cards never link to product detail.

The card’s hover motion is limited to a small image scale and vertical lift. Touch and keyboard states remain usable without hover.

## Page integration

- `HomePage.vue`: adds the complete homepage sample and uses the shared standard card for real market data.
- `MarketListPage.vue`: preserves draft-versus-applied URL query state, pagination, request sequencing, validation messages, and favorite behavior while using the shared card and responsive grid.
- `SellerShopPage.vue`: preserves both request sequences, profile data, tab state, pagination, self-management links, errors, and empty states. It maps on-sale items to `standard` and sold items to non-clickable `sold` cards.
- `MarketDetailPage.vue`: changes only the seller-other-products rendering to the shared compact card. Seller summary, shop route, favorite, cart, buy, reviews, report, errors, and current request ordering remain unchanged.

## Hero asset and degradation

An original `1920 × 720` home hero image will be generated using image generation and committed at `demo-user-ui/src/assets/commerce/home-hero.webp`, targeting approximately 500 KB. It will contain a warm lifestyle scene of unbranded second-hand objects, no text, logos, people, or trademarks. The image sits on the right side of the hero; mobile uses an adjusted `object-position` so its subject remains visible. A CSS warm-gradient scene remains visible when the asset cannot load.

## Error handling and accessibility

Existing loading, errors, retry controls, empty states, disabled controls, and request-isolation checks are retained. New controls use semantic links or buttons, labels, and accessible names. Decorative hero/card fallbacks are hidden from assistive technology where appropriate. The global shell includes safe-area padding for the mobile navigation and body padding so fixed controls do not cover actions.

## Validation

After implementation, run `npm install` or `npm ci` as appropriate, `npm run build:real`, `git diff --check`, `git status --short`, and `git diff --stat`. Inspect the local user app at 390 px, 768 px, 1024 px, and 1440 px, including search-to-market navigation, cart header action/badge, market filtering and pagination, favorite behavior, product detail actions, and seller-shop on-sale/sold behavior. No new large test suite is part of this phase.
