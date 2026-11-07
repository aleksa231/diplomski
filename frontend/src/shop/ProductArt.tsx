import type { CSSProperties } from 'react';
import {
  Apple,
  Banana,
  Beef,
  BookOpen,
  Candy,
  Carrot,
  Cherry,
  Citrus,
  Coffee,
  Drumstick,
  Droplet,
  Egg,
  Fish,
  Laptop,
  LaptopMinimal,
  LaptopMinimalCheck,
  Leaf,
  Milk,
  Salad,
  Shirt,
  ShoppingBag,
  ShoppingBasket,
  Smartphone,
  SmartphoneCharging,
  SmartphoneNfc,
  Tag,
  Wheat,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

interface Palette {
  from: string;
  to: string;
  ink: string;
}

/**
 * Generated products carry no image URLs. Every category keeps its own colour family (blue
 * laptops, violet phones, rose clothing, green groceries, amber books) while the ten shades
 * give each product a set of versions. The icon always describes the actual item.
 */
const PALETTES: Record<string, Palette[]> = {
  laptops: [
    { from: '#0c4a6e', to: '#0284c7', ink: '#e0f2fe' },
    { from: '#1e3a8a', to: '#2563eb', ink: '#dbeafe' },
    { from: '#164e63', to: '#0891b2', ink: '#cffafe' },
    { from: '#075985', to: '#0ea5e9', ink: '#e0f2fe' },
    { from: '#172554', to: '#1d4ed8', ink: '#dbeafe' },
    { from: '#1e40af', to: '#60a5fa', ink: '#dbeafe' },
    { from: '#0e7490', to: '#22d3ee', ink: '#cffafe' },
    { from: '#1e3a8a', to: '#3b82f6', ink: '#dbeafe' },
    { from: '#0c4a6e', to: '#38bdf8', ink: '#e0f2fe' },
    { from: '#083344', to: '#155e75', ink: '#cffafe' },
  ],
  phones: [
    { from: '#312e81', to: '#6d28d9', ink: '#ddd6fe' },
    { from: '#4c1d95', to: '#7c3aed', ink: '#ede9fe' },
    { from: '#312e81', to: '#4f46e5', ink: '#e0e7ff' },
    { from: '#581c87', to: '#9333ea', ink: '#f3e8ff' },
    { from: '#1e1b4b', to: '#6d28d9', ink: '#ddd6fe' },
    { from: '#2e1065', to: '#4338ca', ink: '#e0e7ff' },
    { from: '#3b0764', to: '#7e22ce', ink: '#f3e8ff' },
    { from: '#2e1065', to: '#8b5cf6', ink: '#ede9fe' },
    { from: '#312e81', to: '#a855f7', ink: '#f3e8ff' },
    { from: '#312e81', to: '#6366f1', ink: '#e0e7ff' },
  ],
  clothing: [
    { from: '#831843', to: '#9d174d', ink: '#fbcfe8' },
    { from: '#881337', to: '#be123c', ink: '#fecdd3' },
    { from: '#9d174d', to: '#db2777', ink: '#fce7f3' },
    { from: '#500724', to: '#9d174d', ink: '#fbcfe8' },
    { from: '#831843', to: '#ec4899', ink: '#fce7f3' },
    { from: '#881337', to: '#f43f5e', ink: '#ffe4e6' },
    { from: '#831843', to: '#f472b6', ink: '#fce7f3' },
    { from: '#9f1239', to: '#fb7185', ink: '#ffe4e6' },
    { from: '#500724', to: '#be185d', ink: '#fbcfe8' },
    { from: '#7f1d1d', to: '#e11d48', ink: '#ffe4e6' },
  ],
  groceries: [
    { from: '#14532d', to: '#3f6212', ink: '#d9f99d' },
    { from: '#14532d', to: '#16a34a', ink: '#dcfce7' },
    { from: '#064e3b', to: '#059669', ink: '#d1fae5' },
    { from: '#166534', to: '#22c55e', ink: '#dcfce7' },
    { from: '#365314', to: '#65a30d', ink: '#ecfccb' },
    { from: '#052e16', to: '#15803d', ink: '#dcfce7' },
    { from: '#065f46', to: '#10b981', ink: '#d1fae5' },
    { from: '#3f6212', to: '#84cc16', ink: '#ecfccb' },
    { from: '#064e3b', to: '#34d399', ink: '#d1fae5' },
    { from: '#1a2e05', to: '#4d7c0f', ink: '#ecfccb' },
  ],
  books: [
    { from: '#7c2d12', to: '#a16207', ink: '#fde68a' },
    { from: '#78350f', to: '#b45309', ink: '#fde68a' },
    { from: '#9a3412', to: '#d97706', ink: '#fef3c7' },
    { from: '#713f12', to: '#ca8a04', ink: '#fef9c3' },
    { from: '#7c2d12', to: '#c2410c', ink: '#ffedd5' },
    { from: '#422006', to: '#92400e', ink: '#fef3c7' },
    { from: '#854d0e', to: '#eab308', ink: '#fef9c3' },
    { from: '#7c2d12', to: '#f59e0b', ink: '#fef3c7' },
    { from: '#78350f', to: '#fbbf24', ink: '#fef9c3' },
    { from: '#431407', to: '#b45309', ink: '#fde68a' },
  ],
};

const FALLBACK_PALETTES: Palette[] = [
  { from: '#1e293b', to: '#334155', ink: '#cbd5e1' },
];

/**
 * A few icon variations inside the same category. Every icon is still a laptop, a phone or
 * a garment, so a product can never show an unrelated object.
 */
const CATEGORY_ICON_SETS: Record<string, LucideIcon[]> = {
  laptops: [Laptop, LaptopMinimal, LaptopMinimalCheck],
  phones: [Smartphone, SmartphoneCharging, SmartphoneNfc],
  clothing: [Shirt, ShoppingBag, Tag],
  books: [BookOpen],
};

/**
 * Groceries span milk, meat, fruit and dry goods, so the icon is picked from the product
 * name. Anything without a fitting icon falls back to the basket, never to a wrong item.
 */
const GROCERY_ICONS: Array<[string, LucideIcon]> = [
  ['jabuk', Apple],
  ['banan', Banana],
  ['pomorandž', Citrus],
  ['jagod', Cherry],
  ['šargarep', Carrot],
  ['salat', Salad],
  ['mlijeko', Milk],
  ['jogurt', Milk],
  ['jaj', Egg],
  ['pileć', Drumstick],
  ['junetin', Beef],
  ['losos', Fish],
  ['špaget', Wheat],
  ['riž', Wheat],
  ['brašn', Wheat],
  ['hljeb', Wheat],
  ['šećer', Candy],
  ['čokolad', Candy],
  ['ulje', Droplet],
  ['kafa', Coffee],
  ['čaj', Leaf],
  ['sok', Citrus],
  ['voda', Droplet],
];

export const ART_VARIANTS = 10;

/** Number of thumbnails shown under the main product image. */
export const GALLERY_THUMBS = 5;

function hash(value: string): number {
  let result = 0;
  for (let index = 0; index < value.length; index += 1) {
    result = (result * 31 + value.charCodeAt(index)) % 360;
  }
  return result;
}

/** The version a product shows when no explicit one is selected. */
export function artVariantFor(id: string, category: string): number {
  return hash(`${category}#${id}`) % ART_VARIANTS;
}

/**
 * Five shades spread across the full palette, so the thumbnail strip stays compact while
 * still offering visibly different versions of the same artwork.
 */
export function galleryVariantsFor(id: string, category: string): number[] {
  const base = artVariantFor(id, category);
  const step = ART_VARIANTS / GALLERY_THUMBS;
  return Array.from({ length: GALLERY_THUMBS }, (_, slot) => (base + slot * step) % ART_VARIANTS);
}

function iconFor(category: string, id: string, name?: string): LucideIcon {
  if (category === 'groceries' && name) {
    const lower = name.toLowerCase();
    const match = GROCERY_ICONS.find(([keyword]) => lower.includes(keyword));
    if (match) {
      return match[1];
    }
  }
  const icons = CATEGORY_ICON_SETS[category];
  if (icons && icons.length > 0) {
    return icons[hash(`${category}#${id}`) % icons.length];
  }
  return ShoppingBasket;
}

export function ProductArt({
  id,
  category,
  name,
  size = 'md',
  variant,
}: {
  id: string;
  category: string;
  name?: string;
  size?: 'sm' | 'md' | 'lg';
  variant?: number;
}) {
  const palettes = PALETTES[category] ?? FALLBACK_PALETTES;
  const raw = variant === undefined ? artVariantFor(id, category) : variant;
  const index = ((raw % palettes.length) + palettes.length) % palettes.length;
  const palette = palettes[index];
  const Icon = iconFor(category, id, name);
  const angle = 110 + (hash(`${id}#${index}`) % 130);

  return (
    <div
      className={`product-art product-art-${size}`}
      style={
        {
          backgroundImage: `linear-gradient(${angle}deg, ${palette.from}, ${palette.to})`,
          '--art-ink': palette.ink,
        } as CSSProperties
      }
      aria-hidden="true"
    >
      <Icon strokeWidth={1.15} />
    </div>
  );
}
