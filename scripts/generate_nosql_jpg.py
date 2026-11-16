import os
import time
from PIL import Image
from selenium import webdriver
from selenium.webdriver.edge.options import Options

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
  .box-header {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .box-body {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .divider {
    stroke: #000000;
    stroke-width: 1.2;
  }
  .thin-divider {
    stroke: #000000;
    stroke-width: 0.8;
  }
  .title-stereo {
    font-size: 10px;
    font-style: italic;
    fill: #000000;
    text-anchor: middle;
  }
  .title-main {
    font-size: 14px;
    font-weight: bold;
    fill: #000000;
    text-anchor: middle;
  }
  .title-sub {
    font-size: 9.5px;
    fill: #333333;
    text-anchor: middle;
  }
  .field-text {
    font-size: 11.5px;
    fill: #000000;
  }
  .field-sub {
    font-size: 9.5px;
    font-style: italic;
    fill: #444444;
  }
  .rel-line {
    stroke: #000000;
    stroke-width: 1.2;
    fill: none;
  }
  .rel-label {
    font-size: 10.5px;
    fill: #000000;
    text-anchor: middle;
  }
  .card-label {
    font-size: 11px;
    font-weight: bold;
    fill: #000000;
  }
  .note-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1;
  }
  .note-text {
    font-size: 9.5px;
    fill: #000000;
  }
</style>
</head>
<body>

<svg width="1120" height="740" viewBox="0 0 1120 740">
  <defs>
    <!-- Solid filled diamond for composition (at parent) -->
    <marker id="diamond-start" viewBox="0 0 20 20" refX="10" refY="10" markerWidth="14" markerHeight="14" orient="auto">
      <polygon points="2,10 10,4 18,10 10,16" fill="#000000" stroke="#000000" stroke-width="1"/>
    </marker>
  </defs>

  <!-- ================= RELATIONSHIPS ================= -->

  <!-- 1. products -> Review (Composition) -->
  <line x1="140" y1="390" x2="140" y2="520" class="rel-line" marker-start="url(#diamond-start)" />
  <rect x="148" y="445" width="70" height="18" fill="#ffffff" />
  <text x="152" y="458" class="rel-label" text-anchor="start">ugniježđeno</text>
  <text x="125" y="510" class="card-label">0..*</text>

  <!-- 2. orders -> OrderItem (Composition) -->
  <line x1="565" y1="340" x2="565" y2="500" class="rel-line" marker-start="url(#diamond-start)" />
  <rect x="573" y="410" width="70" height="18" fill="#ffffff" />
  <text x="577" y="423" class="rel-label" text-anchor="start">ugniježđeno</text>
  <text x="550" y="490" class="card-label">1..*</text>

  <!-- 3. orders -> OrderStatusHistory (Composition) -->
  <path d="M 700 340 L 700 420 L 825 420 L 825 500" class="rel-line" marker-start="url(#diamond-start)" />
  <rect x="740" y="411" width="70" height="18" fill="#ffffff" />
  <text x="775" y="424" class="rel-label">ugniježđeno</text>
  <text x="810" y="490" class="card-label">1..*</text>

  <!-- 4. Note connection (dashed line) -->
  <line x1="250" y1="390" x2="310" y2="530" stroke="#000000" stroke-width="1" stroke-dasharray="4,4" />


  <!-- ================= NOTE BOX ================= -->
  <g transform="translate(270, 530)">
    <!-- Dog-eared polygon -->
    <polygon points="0,0 140,0 155,15 155,120 0,120" class="note-box" />
    <polygon points="140,0 140,15 155,15" fill="#f0f0f0" stroke="#000000" stroke-width="1" />
    <text x="10" y="20" class="note-text" font-weight="bold">Napomena (NoSQL):</text>
    <text x="10" y="38" class="note-text">Između kolekcija ne</text>
    <text x="10" y="53" class="note-text">postoje strani ključevi.</text>
    <text x="10" y="68" class="note-text">Dokument sadrži sve</text>
    <text x="10" y="83" class="note-text">podatke potrebne za</text>
    <text x="10" y="98" class="note-text">jedno čitanje (ugniježđivanje</text>
    <text x="10" y="113" class="note-text">i denormalizacija).</text>
  </g>


  <!-- ================= ENTITY 1: products ================= -->
  <g transform="translate(30, 40)">
    <rect width="280" height="350" class="box-body" />
    <rect width="280" height="52" class="box-header" />
    <text x="140" y="17" class="title-stereo">«kolekcija»</text>
    <text x="140" y="33" class="title-main">products</text>
    <text x="140" y="46" class="title-sub">(MongoDB kolekcija)</text>
    <line x1="0" y1="52" x2="280" y2="52" class="divider" />
    
    <g transform="translate(14, 72)" class="field-text">
      <text y="0"><tspan font-weight="bold">_id</tspan> : string «PK»</text>
      <text y="20"><tspan font-weight="bold">sku</tspan> : string «jedinstveno»</text>
      <text y="40">name : string</text>
      <text y="60">category : string</text>
      <text y="80">price : double</text>
      <text y="100">stock : int</text>
      <text y="120">reviews : List&lt;Review&gt;</text>
      <text y="140">reviewCount : int</text>
      <text y="160">ratingAvg : double</text>
      <text y="180">createdAt : timestamp</text>
      <text y="200">updatedAt : timestamp</text>
    </g>

    <line x1="0" y1="292" x2="280" y2="292" class="thin-divider" />
    <g transform="translate(14, 312)">
      <text y="0" class="field-text"><tspan font-weight="bold">attributes</tspan> : Map&lt;string, any&gt;</text>
      <text y="18" class="field-sub">(slobodni dinamički atributi po kategoriji)</text>
    </g>
  </g>


  <!-- ================= ENTITY 2: Review (Embedded) ================= -->
  <g transform="translate(30, 520)">
    <rect width="220" height="135" class="box-body" />
    <rect width="220" height="48" class="box-header" />
    <text x="110" y="16" class="title-stereo">«ugniježđeno»</text>
    <text x="110" y="31" class="title-main">Review</text>
    <text x="110" y="43" class="title-sub">(ugniježđeni dokument)</text>
    <line x1="0" y1="48" x2="220" y2="48" class="divider" />
    
    <g transform="translate(14, 70)" class="field-text">
      <text y="0">rating : int</text>
      <text y="22">text : string</text>
      <text y="44">createdAt : timestamp</text>
    </g>
  </g>


  <!-- ================= ENTITY 3: orders ================= -->
  <g transform="translate(450, 55)">
    <rect width="295" height="285" class="box-body" />
    <rect width="295" height="52" class="box-header" />
    <text x="147" y="17" class="title-stereo">«kolekcija»</text>
    <text x="147" y="33" class="title-main">orders</text>
    <text x="147" y="46" class="title-sub">(MongoDB kolekcija)</text>
    <line x1="0" y1="52" x2="295" y2="52" class="divider" />
    
    <g transform="translate(14, 74)" class="field-text">
      <text y="0"><tspan font-weight="bold">_id</tspan> : string «PK»</text>
      <text y="22">customerRef : string</text>
      <text y="44">customerName : string</text>
      <text y="66">customerEmail : string</text>
      <text y="88">items : List&lt;OrderItem&gt;</text>
      <text y="110">total : double</text>
      <text y="132">status : string</text>
      <text y="154">statusHistory : List&lt;OrderStatusHistory&gt;</text>
      <text y="176">createdAt : timestamp</text>
    </g>
  </g>


  <!-- ================= ENTITY 4: OrderItem (Embedded) ================= -->
  <g transform="translate(450, 500)">
    <rect width="235" height="175" class="box-body" />
    <rect width="235" height="48" class="box-header" />
    <text x="117" y="16" class="title-stereo">«ugniježđeno»</text>
    <text x="117" y="31" class="title-main">OrderItem</text>
    <text x="117" y="43" class="title-sub">(ugniježđeni dokument)</text>
    <line x1="0" y1="48" x2="235" y2="48" class="divider" />
    
    <g transform="translate(14, 68)" class="field-text">
      <text y="0">productId : string</text>
      <text y="20">quantity : int</text>
    </g>

    <line x1="0" y1="100" x2="235" y2="100" class="thin-divider" />
    <g transform="translate(14, 118)">
      <text y="0" class="field-text">productName : string</text>
      <text y="13" class="field-sub">(denormalizovano)</text>
      <text y="30" class="field-text">price : double</text>
      <text y="43" class="field-sub">(cijena u trenutku kupovine)</text>
    </g>
  </g>


  <!-- ================= ENTITY 5: OrderStatusHistory (Embedded) ================= -->
  <g transform="translate(715, 500)">
    <rect width="220" height="125" class="box-body" />
    <rect width="220" height="48" class="box-header" />
    <text x="110" y="16" class="title-stereo">«ugniježđeno»</text>
    <text x="110" y="31" class="title-main">OrderStatusHistory</text>
    <text x="110" y="43" class="title-sub">(ugniježđeni dokument)</text>
    <line x1="0" y1="48" x2="220" y2="48" class="divider" />
    
    <g transform="translate(14, 72)" class="field-text">
      <text y="0">status : string</text>
      <text y="24">timestamp : timestamp</text>
    </g>
  </g>


  <!-- ================= ENTITY 6: carts ================= -->
  <g transform="translate(790, 95)">
    <rect width="270" height="150" class="box-body" />
    <rect width="270" height="52" class="box-header" />
    <text x="135" y="17" class="title-stereo">«kolekcija / skladište»</text>
    <text x="135" y="33" class="title-main">carts</text>
    <text x="135" y="46" class="title-sub">(Redis keš / MongoDB kolekcija)</text>
    <line x1="0" y1="52" x2="270" y2="52" class="divider" />
    
    <g transform="translate(14, 74)" class="field-text">
      <text y="0"><tspan font-weight="bold">_id</tspan> : string «PK»</text>
      <text y="24">items : Map&lt;productId, quantity&gt;</text>
      <text y="48">updatedAt : timestamp</text>
    </g>
  </g>

</svg>

</body>
</html>
"""

temp_html_path = os.path.abspath("temp_nosql_schema.html")
temp_png_path = os.path.abspath("temp_nosql_screenshot.png")
output_jpg_path = os.path.abspath("sema_nosql_baze_klikmarket.jpg")
docs_jpg_path = os.path.abspath("docs/sema_nosql_baze_klikmarket.jpg")
artifact_jpg_path = os.path.abspath(r"C:\Users\Ivana\.gemini\antigravity\brain\b10f1e45-d7c5-4cda-a792-4d82f85cdd80\sema_nosql_baze_klikmarket.jpg")

with open(temp_html_path, "w", encoding="utf-8") as f:
    f.write(html_content)

options = Options()
options.add_argument("--headless=new")
options.add_argument("--disable-gpu")
options.add_argument("--window-size=1300,900")
options.add_argument("--hide-scrollbars")
options.add_argument("--force-device-scale-factor=2")

driver = webdriver.Edge(options=options)
driver.get(f"file:///{temp_html_path}")
time.sleep(1)

svg_element = driver.find_element("tag name", "svg")
svg_element.screenshot(temp_png_path)
driver.quit()

img = Image.open(temp_png_path)
rgb_img = Image.new("RGB", img.size, (255, 255, 255))
if img.mode in ("RGBA", "LA"):
    rgb_img.paste(img, mask=img.split()[-1])
else:
    rgb_img.paste(img)

rgb_img.save(output_jpg_path, "JPEG", quality=98)
rgb_img.save(docs_jpg_path, "JPEG", quality=98)
rgb_img.save(artifact_jpg_path, "JPEG", quality=98)

if os.path.exists(temp_html_path):
    os.remove(temp_html_path)
if os.path.exists(temp_png_path):
    os.remove(temp_png_path)
if os.path.exists("crop_nosql_test.png"):
    os.remove("crop_nosql_test.png")
if os.path.exists("crop_nosql_bottom.png"):
    os.remove("crop_nosql_bottom.png")
if os.path.exists("crop_nosql_carts.png"):
    os.remove("crop_nosql_carts.png")

print("Done! High-resolution NoSQL JPG generated and saved.")
