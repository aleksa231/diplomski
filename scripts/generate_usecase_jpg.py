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
  .system-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.5;
  }
  .system-title {
    font-size: 14.5px;
    font-weight: bold;
    fill: #000000;
  }
  .actor-line {
    stroke: #000000;
    stroke-width: 1.5;
    fill: none;
  }
  .actor-head {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.5;
  }
  .actor-name {
    font-size: 13.5px;
    font-weight: bold;
    fill: #000000;
    text-anchor: middle;
  }
  .usecase-ellipse {
    fill: #fbfbfd;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .usecase-text {
    font-size: 11.5px;
    fill: #000000;
    text-anchor: middle;
  }
  .assoc-line {
    stroke: #000000;
    stroke-width: 1.2;
    fill: none;
  }
  .extend-line {
    stroke: #000000;
    stroke-width: 1.2;
    stroke-dasharray: 5,4;
    fill: none;
  }
  .rel-stereo {
    font-size: 10px;
    font-style: italic;
    fill: #000000;
    text-anchor: middle;
  }
  .section-label {
    font-size: 11px;
    font-weight: bold;
    fill: #4b5563;
    letter-spacing: 0.6px;
  }
</style>
</head>
<body>

<svg width="1220" height="740" viewBox="0 0 1220 740">
  <defs>
    <!-- Open arrowhead for <<extend>> pointing to base use case -->
    <marker id="arrow-open" viewBox="0 0 16 16" refX="14" refY="8" markerWidth="9" markerHeight="9" orient="auto">
      <path d="M 2 2 L 14 8 L 2 14" fill="none" stroke="#000000" stroke-width="1.5" />
    </marker>
  </defs>

  <!-- ================= SYSTEM BOUNDARY ================= -->
  <rect x="175" y="40" width="870" height="660" class="system-box" />
  <text x="195" y="68" class="system-title">Sistem: KlikMarket</text>

  <!-- Section indicators -->
  <text x="210" y="96" class="section-label">MODUL E-PRODAVNICE</text>
  <text x="660" y="96" class="section-label">EKSPERIMENTALNI BENCHMARK MODUL</text>
  <line x1="630" y1="82" x2="630" y2="680" stroke="#cbd5e1" stroke-width="1.2" stroke-dasharray="4,4" />


  <!-- ================= ACTOR 1: KUPAC (LEFT) ================= -->
  <g transform="translate(85, 310)">
    <!-- Head -->
    <circle cx="0" cy="0" r="18" class="actor-head" />
    <!-- Body -->
    <line x1="0" y1="18" x2="0" y2="60" class="actor-line" />
    <!-- Arms -->
    <line x1="-24" y1="34" x2="24" y2="34" class="actor-line" />
    <!-- Legs -->
    <line x1="0" y1="60" x2="-20" y2="105" class="actor-line" />
    <line x1="0" y1="60" x2="20" y2="105" class="actor-line" />
    <!-- Label -->
    <text x="0" y="128" class="actor-name">Kupac</text>
  </g>


  <!-- ================= ACTOR 2: ISTRAŽIVAČ (RIGHT) ================= -->
  <g transform="translate(1125, 310)">
    <!-- Head -->
    <circle cx="0" cy="0" r="18" class="actor-head" />
    <!-- Body -->
    <line x1="0" y1="18" x2="0" y2="60" class="actor-line" />
    <!-- Arms -->
    <line x1="-24" y1="34" x2="24" y2="34" class="actor-line" />
    <!-- Legs -->
    <line x1="0" y1="60" x2="-20" y2="105" class="actor-line" />
    <line x1="0" y1="60" x2="20" y2="105" class="actor-line" />
    <!-- Label -->
    <text x="0" y="128" class="actor-name">Istraživač</text>
  </g>


  <!-- ================= USE CASES: KUPAC ================= -->

  <!-- UC 1: Pregled popularnih proizvoda -->
  <g transform="translate(295, 140)">
    <ellipse cx="0" cy="0" rx="115" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Pregled popularnih proizvoda</text>
  </g>

  <!-- UC 2: Pretraga i filtriranje kataloga proizvoda -->
  <g transform="translate(305, 230)">
    <ellipse cx="0" cy="0" rx="125" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Pretraga i filtriranje kataloga</text>
  </g>

  <!-- UC 3: Pregled detalja proizvoda (Base UC) -->
  <g transform="translate(290, 320)">
    <ellipse cx="0" cy="0" rx="105" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Pregled detalja proizvoda</text>
  </g>

  <!-- UC 4: Pisanje recenzije (Extension UC) -->
  <g transform="translate(540, 320)">
    <ellipse cx="0" cy="0" rx="72" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Pisanje recenzije</text>
  </g>

  <!-- UC 5: Upravljanje korpom -->
  <g transform="translate(295, 415)">
    <ellipse cx="0" cy="0" rx="110" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Upravljanje korpom</text>
  </g>

  <!-- UC 6: Kreiranje narudžbe -->
  <g transform="translate(295, 510)">
    <ellipse cx="0" cy="0" rx="110" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Kreiranje narudžbe</text>
  </g>

  <!-- UC 7: Pregled istorije narudžbi -->
  <g transform="translate(295, 605)">
    <ellipse cx="0" cy="0" rx="115" ry="24" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Pregled istorije narudžbi</text>
  </g>


  <!-- ================= USE CASES: ISTRAŽIVAČ ================= -->

  <!-- UC 8: Odabir scenarija mjerenja -->
  <g transform="translate(830, 150)">
    <ellipse cx="0" cy="0" rx="135" ry="25" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Odabir scenarija mjerenja (S1–S7)</text>
  </g>

  <!-- UC 9: Podešavanje parametara eksperimenta -->
  <g transform="translate(830, 250)">
    <ellipse cx="0" cy="0" rx="145" ry="25" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Podešavanje parametara eksperimenta</text>
  </g>

  <!-- UC 10: Praćenje mjerenja u realnom vremenu -->
  <g transform="translate(830, 350)">
    <ellipse cx="0" cy="0" rx="145" ry="25" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Praćenje mjerenja u realnom vremenu</text>
  </g>

  <!-- UC 11: Pregled i upoređivanje rezultata -->
  <g transform="translate(830, 450)">
    <ellipse cx="0" cy="0" rx="140" ry="25" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Pregled i upoređivanje rezultata</text>
  </g>

  <!-- UC 12: Izvoz rezultata -->
  <g transform="translate(830, 550)">
    <ellipse cx="0" cy="0" rx="115" ry="25" class="usecase-ellipse" />
    <text x="0" y="4" class="usecase-text">Izvoz rezultata (JSON / CSV)</text>
  </g>


  <!-- ================= ASSOCIATIONS: KUPAC ================= -->
  <!-- Standard UML: solid line WITHOUT arrowheads -->
  <line x1="85" y1="340" x2="182" y2="148" class="assoc-line" />
  <line x1="85" y1="340" x2="182" y2="232" class="assoc-line" />
  <line x1="85" y1="340" x2="185" y2="320" class="assoc-line" />
  <line x1="85" y1="350" x2="187" y2="415" class="assoc-line" />
  <line x1="85" y1="360" x2="187" y2="508" class="assoc-line" />
  <line x1="85" y1="370" x2="182" y2="602" class="assoc-line" />


  <!-- ================= RELATIONSHIP: <<extend>> ================= -->
  <!-- Correct UML: arrow points FROM extending UC (Pisanje recenzije) TO base UC (Pregled detalja) -->
  <line x1="468" y1="320" x2="400" y2="320" class="extend-line" marker-end="url(#arrow-open)" />
  <rect x="408" y="303" width="50" height="15" fill="#ffffff" />
  <text x="433" y="314" class="rel-stereo">«extend»</text>


  <!-- ================= ASSOCIATIONS: ISTRAŽIVAČ ================= -->
  <!-- Standard UML: solid line WITHOUT arrowheads -->
  <line x1="1125" y1="340" x2="965" y2="158" class="assoc-line" />
  <line x1="1125" y1="340" x2="975" y2="256" class="assoc-line" />
  <line x1="1125" y1="340" x2="975" y2="350" class="assoc-line" />
  <line x1="1125" y1="350" x2="970" y2="446" class="assoc-line" />
  <line x1="1125" y1="360" x2="945" y2="546" class="assoc-line" />

</svg>

</body>
</html>
"""

temp_html_path = os.path.abspath("temp_usecase_schema.html")
temp_png_path = os.path.abspath("temp_usecase_screenshot.png")
output_jpg_path = os.path.abspath("dijagram_slucajeva_upotrebe_klikmarket.jpg")
docs_jpg_path = os.path.abspath("docs/dijagram_slucajeva_upotrebe_klikmarket.jpg")
artifact_jpg_path = os.path.abspath(r"C:\Users\Ivana\.gemini\antigravity\brain\b10f1e45-d7c5-4cda-a792-4d82f85cdd80\dijagram_slucajeva_upotrebe_klikmarket.jpg")

with open(temp_html_path, "w", encoding="utf-8") as f:
    f.write(html_content)

options = Options()
options.add_argument("--headless=new")
options.add_argument("--disable-gpu")
options.add_argument("--window-size=1300,850")
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
if os.path.exists("crop_uc_verify.png"):
    os.remove("crop_uc_verify.png")
if os.path.exists("crop_uc_include.png"):
    os.remove("crop_uc_include.png")
if os.path.exists("crop_uc_extend.png"):
    os.remove("crop_uc_extend.png")

print("Updated use case diagram JPG generated and saved!")
