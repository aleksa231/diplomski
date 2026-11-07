/** Presentation helpers shared by the storefront pages. */

export interface ShopCategory {
  value: string;
  label: string;
}

/** The five categories the deterministic generator produces (data-model.md, section 2). */
export const SHOP_CATEGORIES: ShopCategory[] = [
  { value: 'laptops', label: 'Laptopi' },
  { value: 'phones', label: 'Telefoni' },
  { value: 'clothing', label: 'Odjeća' },
  { value: 'groceries', label: 'Namirnice' },
  { value: 'books', label: 'Knjige' },
];

export function categoryLabel(category: string): string {
  return SHOP_CATEGORIES.find((item) => item.value === category)?.label ?? category;
}

const ATTRIBUTE_LABELS: Record<string, string> = {
  cpu: 'Procesor',
  ramGb: 'RAM',
  storageGb: 'Skladište',
  screenInch: 'Ekran',
  gpu: 'Grafička karta',
  weightKg: 'Težina',
  os: 'Operativni sistem',
  batteryMah: 'Baterija',
  cameraMp: 'Kamera',
  fiveG: '5G',
  size: 'Veličina',
  color: 'Boja',
  material: 'Materijal',
  gender: 'Pol',
  season: 'Sezona',
  weightG: 'Težina',
  organic: 'Organsko',
  expiryDays: 'Rok trajanja',
  origin: 'Porijeklo',
  allergens: 'Alergeni',
  author: 'Autor',
  pages: 'Stranice',
  language: 'Jezik',
  isbn: 'ISBN',
  genre: 'Žanr',
};

const ATTRIBUTE_UNITS: Record<string, string> = {
  ramGb: 'GB',
  storageGb: 'GB',
  screenInch: '"',
  weightKg: 'kg',
  batteryMah: 'mAh',
  cameraMp: 'MP',
  weightG: 'g',
  expiryDays: 'dana',
  pages: 'str.',
};

export function attributeLabel(key: string): string {
  return ATTRIBUTE_LABELS[key] ?? key;
}

/** Decimals use the Serbian comma (13,3) while keeping large integers grouped-free (2048). */
const DECIMAL = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2, useGrouping: false });

const FIXED_DECIMALS = new Map<number, Intl.NumberFormat>();

export function decimal(value: number, digits = 1): string {
  let formatter = FIXED_DECIMALS.get(digits);
  if (!formatter) {
    formatter = new Intl.NumberFormat('de-DE', {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
      useGrouping: false,
    });
    FIXED_DECIMALS.set(digits, formatter);
  }
  return formatter.format(value);
}

export function attributeValue(key: string, value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'boolean') {
    return value ? 'Da' : 'Ne';
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.map((item) => attributeValue(key, item)).join(', ');
  }
  const unit = ATTRIBUTE_UNITS[key];
  const text = typeof value === 'number' ? DECIMAL.format(value) : String(value);
  return unit ? `${text} ${unit}` : text;
}

const EURO = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

export function price(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : EURO.format(value);
}
