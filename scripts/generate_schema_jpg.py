import os
import time
from PIL import Image
from selenium import webdriver
from selenium.webdriver.edge.options import Options

# HTML with pure vector SVG styled exactly like the original diagram (black text, white background)
html_content = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background-color: #ffffff;
    font-family: Arial, "Helvetica Neue", Helvetica, sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 30px;
  }
  svg {
    background-color: #ffffff;
  }
  .table-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .table-header-text {
    font-size: 13.5px;
    font-weight: bold;
    fill: #000000;
    text-anchor: middle;
  }
  .table-divider {
    stroke: #000000;
    stroke-width: 1.2;
  }
  .col-text {
    font-size: 12px;
    fill: #000000;
  }
  .rel-line {
    stroke: #000000;
    stroke-width: 1.2;
    fill: none;
  }
  .rel-label {
    font-size: 11px;
    fill: #000000;
    background-color: #ffffff;
  }
</style>
</head>
<body>

<svg width="1240" height="1060" viewBox="0 0 1240 1060">
  <defs>
    <!-- Crow's foot markers -->
    <!-- Two parallel vertical bars (Mandatory One: ||) -->
    <marker id="one-start" viewBox="0 0 20 20" refX="15" refY="10" markerWidth="14" markerHeight="14" orient="auto">
      <line x1="6" y1="2" x2="6" y2="18" stroke="#000000" stroke-width="1.3" />
      <line x1="12" y1="2" x2="12" y2="18" stroke="#000000" stroke-width="1.3" />
    </marker>
    <!-- Zero or many (Crow's foot with circle: >o) -->
    <marker id="crow-end" viewBox="0 0 24 20" refX="21" refY="10" markerWidth="16" markerHeight="14" orient="auto">
      <circle cx="6" cy="10" r="4.5" fill="#ffffff" stroke="#000000" stroke-width="1.2" />
      <line x1="12" y1="10" x2="22" y2="2" stroke="#000000" stroke-width="1.3" />
      <line x1="12" y1="10" x2="22" y2="18" stroke="#000000" stroke-width="1.3" />
      <line x1="12" y1="10" x2="22" y2="10" stroke="#000000" stroke-width="1.3" />
    </marker>
    <!-- Zero or one (Circle and vertical bar: o|) -->
    <marker id="zero-one-end" viewBox="0 0 24 20" refX="21" refY="10" markerWidth="16" markerHeight="14" orient="auto">
      <circle cx="8" cy="10" r="4.5" fill="#ffffff" stroke="#000000" stroke-width="1.2" />
      <line x1="18" y1="2" x2="18" y2="18" stroke="#000000" stroke-width="1.3" />
    </marker>
  </defs>

  <!-- ================= RELATIONSHIP LINES ================= -->

  <!-- 1. orders -> order_status_history (prati) -->
  <path d="M 670 120 L 890 65" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="760" y="80" width="35" height="16" fill="#ffffff" />
  <text x="777" y="92" class="rel-label" text-anchor="middle">prati</text>

  <!-- 2. orders -> order_item (sadrži) -->
  <path d="M 670 160 L 880 230" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="750" y="184" width="45" height="16" fill="#ffffff" />
  <text x="772" y="196" class="rel-label" text-anchor="middle">sadrži</text>

  <!-- 3. product -> order_item (referencira) - ISPRAVLJENO: PUNA LINIJA! -->
  <path d="M 690 440 L 880 325" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="740" y="370" width="70" height="16" fill="#ffffff" />
  <text x="775" y="382" class="rel-label" text-anchor="middle">referencira</text>

  <!-- 5. product -> review (ima) -->
  <path d="M 700 550 L 890 550" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="780" y="541" width="30" height="16" fill="#ffffff" />
  <text x="795" y="553" class="rel-label" text-anchor="middle">ima</text>

  <!-- 6. product -> product_attribute_value (ima) -->
  <path d="M 690 675 L 840 705" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />

  <!-- 7. category -> product (sadrži) -->
  <path d="M 290 320 L 430 580" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="335" y="420" width="45" height="16" fill="#ffffff" />
  <text x="357" y="432" class="rel-label" text-anchor="middle">sadrži</text>

  <!-- 8. category -> attribute_definition (definiše) -->
  <path d="M 290 355 L 430 740" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="330" y="520" width="55" height="16" fill="#ffffff" />
  <text x="357" y="532" class="rel-label" text-anchor="middle">definiše</text>

  <!-- 9. attribute_definition -> product_attribute_value -->
  <path d="M 690 765 L 840 765" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />

  <!-- 10. cart -> cart_item (sadrži) -->
  <path d="M 680 945 L 890 945" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="765" y="936" width="45" height="16" fill="#ffffff" />
  <text x="787" y="948" class="rel-label" text-anchor="middle">sadrži</text>

  <!-- 11. product -> cart_item (referencira) - ISPRAVLJENO: PUNA LINIJA! -->
  <path d="M 685 690 Q 720 860 890 920" class="rel-line" marker-start="url(#one-start)" marker-end="url(#crow-end)" />
  <rect x="715" y="805" width="70" height="16" fill="#ffffff" />
  <text x="750" y="817" class="rel-label" text-anchor="middle">referencira</text>


  <!-- ================= TABLES / ENTITIES ================= -->

  <!-- TABLE: orders -->
  <g transform="translate(430, 90)">
    <rect width="240" height="155" class="table-box" />
    <text x="120" y="24" class="table-header-text">orders</text>
    <line x1="0" y1="34" x2="240" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• customer_ref : varchar(100)</text>
      <text y="44">• total : numeric(12,2)</text>
      <text y="66">• status : varchar(50)</text>
      <text y="88">• created_at : timestamp</text>
    </g>
  </g>

  <!-- TABLE: order_status_history -->
  <g transform="translate(890, 20)">
    <rect width="250" height="135" class="table-box" />
    <text x="125" y="24" class="table-header-text">order_status_history</text>
    <line x1="0" y1="34" x2="250" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• order_id : varchar(36) «FK»</text>
      <text y="44">• status : varchar(50)</text>
      <text y="66">• created_at : timestamp</text>
    </g>
  </g>

  <!-- TABLE: order_item [ISPRAVLJENO: product_id «FK»] -->
  <g transform="translate(880, 180)">
    <rect width="270" height="180" class="table-box" />
    <text x="135" y="24" class="table-header-text">order_item</text>
    <line x1="0" y1="34" x2="270" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• order_id : varchar(36) «FK»</text>
      <text y="44" font-weight="bold">• product_id : varchar(36) «FK»</text>
      <text y="66">• product_name : varchar(255)</text>
      <text y="88">• price_snapshot : numeric(10,2)</text>
      <text y="110">• quantity : int</text>
    </g>
  </g>

  <!-- TABLE: category -->
  <g transform="translate(30, 290)">
    <rect width="260" height="115" class="table-box" />
    <text x="130" y="24" class="table-header-text">category</text>
    <line x1="0" y1="34" x2="260" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• name : varchar(100) «jedinstveno»</text>
      <text y="44">• slug : varchar(100) «jedinstveno»</text>
    </g>
  </g>

  <!-- TABLE: product -->
  <g transform="translate(430, 420)">
    <rect width="270" height="275" class="table-box" />
    <text x="135" y="24" class="table-header-text">product</text>
    <line x1="0" y1="34" x2="270" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• category_id : varchar(36) «FK»</text>
      <text y="44">• sku : varchar(64) «jedinstveno»</text>
      <text y="66">• name : varchar(255)</text>
      <text y="88">• price : numeric(10,2)</text>
      <text y="110">• stock : int</text>
      <text y="132">• review_count : int</text>
      <text y="154">• rating_avg : numeric(3,2)</text>
      <text y="176">• created_at : timestamp</text>
      <text y="198">• updated_at : timestamp</text>
    </g>
  </g>

  <!-- TABLE: review -->
  <g transform="translate(890, 505)">
    <rect width="250" height="150" class="table-box" />
    <text x="125" y="24" class="table-header-text">review</text>
    <line x1="0" y1="34" x2="250" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• product_id : varchar(36) «FK»</text>
      <text y="44">• rating : int</text>
      <text y="66">• text : text</text>
      <text y="88">• created_at : timestamp</text>
    </g>
  </g>

  <!-- TABLE: attribute_definition -->
  <g transform="translate(430, 720)">
    <rect width="260" height="135" class="table-box" />
    <text x="130" y="24" class="table-header-text">attribute_definition</text>
    <line x1="0" y1="34" x2="260" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• category_id : varchar(36) «FK»</text>
      <text y="44">• name : varchar(100)</text>
      <text y="66">• data_type : varchar(20)</text>
    </g>
  </g>

  <!-- TABLE: product_attribute_value -->
  <g transform="translate(840, 680)">
    <rect width="320" height="175" class="table-box" />
    <text x="160" y="24" class="table-header-text">product_attribute_value</text>
    <line x1="0" y1="34" x2="320" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• product_id : varchar(36) «FK»</text>
      <text y="44">• attribute_definition_id : varchar(36) «FK»</text>
      <text y="66">• string_value : text</text>
      <text y="88">• number_value : numeric(12,4)</text>
      <text y="110">• boolean_value : boolean</text>
    </g>
  </g>

  <!-- TABLE: cart -->
  <g transform="translate(460, 890)">
    <rect width="220" height="110" class="table-box" />
    <text x="110" y="24" class="table-header-text">cart</text>
    <line x1="0" y1="34" x2="220" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• created_at : timestamp</text>
      <text y="44">• expires_at : timestamp</text>
    </g>
  </g>

  <!-- TABLE: cart_item [ISPRAVLJENO: product_id «FK»] -->
  <g transform="translate(890, 880)">
    <rect width="260" height="135" class="table-box" />
    <text x="130" y="24" class="table-header-text">cart_item</text>
    <line x1="0" y1="34" x2="260" y2="34" class="table-divider" />
    <g transform="translate(14, 55)" class="col-text">
      <text y="0">• id : varchar(36) «PK»</text>
      <text y="22">• cart_id : varchar(36) «FK»</text>
      <text y="44" font-weight="bold">• product_id : varchar(36) «FK»</text>
      <text y="66">• quantity : int</text>
    </g>
  </g>

</svg>

</body>
</html>
"""

temp_html_path = os.path.abspath("temp_schema.html")
temp_png_path = os.path.abspath("temp_screenshot.png")
output_jpg_path = os.path.abspath("sema_relacione_baze_klikmarket.jpg")
docs_jpg_path = os.path.abspath("docs/sema_relacione_baze_klikmarket.jpg")

with open(temp_html_path, "w", encoding="utf-8") as f:
    f.write(html_content)

print(f"Temporary HTML written to {temp_html_path}")

# Run headless Edge to render and screenshot
options = Options()
options.add_argument("--headless=new")
options.add_argument("--disable-gpu")
options.add_argument("--window-size=1400,1200")
options.add_argument("--hide-scrollbars")
options.add_argument("--force-device-scale-factor=2")  # 2x scale for ultra crisp text!

driver = webdriver.Edge(options=options)
driver.get(f"file:///{temp_html_path}")
time.sleep(1)

# Find svg element or take full screenshot
svg_element = driver.find_element("tag name", "svg")
svg_element.screenshot(temp_png_path)
driver.quit()

print(f"Crisp PNG screenshot saved to {temp_png_path}")

# Convert PNG to high-quality JPG with pure white background
img = Image.open(temp_png_path)
rgb_img = Image.new("RGB", img.size, (255, 255, 255))
if img.mode in ("RGBA", "LA"):
    rgb_img.paste(img, mask=img.split()[-1])
else:
    rgb_img.paste(img)

rgb_img.save(output_jpg_path, "JPEG", quality=98)
rgb_img.save(docs_jpg_path, "JPEG", quality=98)

print(f"Saved JPG image to: {output_jpg_path}")
print(f"Saved JPG copy to: {docs_jpg_path}")

# Clean up temporary files
if os.path.exists(temp_html_path):
    os.remove(temp_html_path)
if os.path.exists(temp_png_path):
    os.remove(temp_png_path)
print("Done!")
