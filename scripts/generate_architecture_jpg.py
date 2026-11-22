import os
import time
from PIL import Image, ImageFilter
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
    padding: 25px;
  }
  svg { background-color: #ffffff; }
  .host-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.8;
  }
  .group-box {
    fill: #fcfcfd;
    stroke: #000000;
    stroke-width: 1.5;
  }
  .node-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.3;
  }
  .node-top {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.3;
  }
  .node-side {
    fill: #e4e4e7;
    stroke: #000000;
    stroke-width: 1.3;
  }
  .db-cylinder {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.5;
  }
  .db-top {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.5;
  }
  .folder-tab {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.3;
  }
  .line-flow {
    stroke: #000000;
    stroke-width: 1.6;
    fill: none;
  }
  .line-dash {
    stroke: #000000;
    stroke-width: 1.5;
    stroke-dasharray: 5,4;
    fill: none;
  }
  .title-main {
    font-size: 16px;
    font-weight: bold;
    fill: #000000;
  }
  .title-group {
    font-size: 14px;
    font-weight: bold;
    fill: #000000;
  }
  .node-text {
    font-size: 12.5px;
    font-weight: bold;
    fill: #000000;
    text-anchor: middle;
  }
  .node-sub {
    font-size: 11px;
    fill: #444444;
    text-anchor: middle;
  }
  .label-text {
    font-size: 11px;
    fill: #000000;
  }
  .explain-text {
    font-size: 11.8px;
    fill: #1e293b;
  }
</style>
</head>
<body>

<svg width="1400" height="1020" viewBox="0 0 1400 1020">
  <defs>
    <marker id="arrow" viewBox="0 0 16 16" refX="14" refY="8" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M 2 3 L 14 8 L 2 13 Z" fill="#000000" />
    </marker>
    <marker id="arrow-open" viewBox="0 0 16 16" refX="14" refY="8" markerWidth="10" markerHeight="10" orient="auto">
      <path d="M 2 2 L 14 8 L 2 14" fill="none" stroke="#000000" stroke-width="1.6" />
    </marker>
  </defs>

  <!-- DOCKER HOST BOUNDARY -->
  <rect x="25" y="25" width="1350" height="970" rx="4" class="host-box" />
  <text x="700" y="58" class="title-main" text-anchor="middle">Fizički računar (Docker host)</text>

  <!-- ================= 1. UPORE-DIVI SERVERSKI SERVISI ================= -->
  <g transform="translate(55, 85)">
    <rect width="290" height="300" rx="3" class="group-box" />
    <text x="145" y="30" class="title-group" text-anchor="middle">Uporedivi serverski servisi</text>

    <!-- relational -->
    <g transform="translate(40, 55)">
      <polygon points="0,8 12,0 210,0 198,8" class="node-top" />
      <polygon points="198,8 210,0 210,42 198,50" class="node-side" />
      <rect x="0" y="8" width="198" height="42" class="node-box" />
      <text x="99" y="28" class="node-text">relational</text>
      <text x="99" y="42" class="node-sub">(:8083, EAV model)</text>
    </g>

    <!-- nosql-blocking -->
    <g transform="translate(40, 135)">
      <polygon points="0,8 12,0 210,0 198,8" class="node-top" />
      <polygon points="198,8 210,0 210,42 198,50" class="node-side" />
      <rect x="0" y="8" width="198" height="42" class="node-box" />
      <text x="99" y="28" class="node-text">nosql-blocking</text>
      <text x="99" y="42" class="node-sub">(:8081, MVC + Mongo/Redis)</text>
    </g>

    <!-- nosql-reactive -->
    <g transform="translate(40, 215)">
      <polygon points="0,8 12,0 210,0 198,8" class="node-top" />
      <polygon points="198,8 210,0 210,42 198,50" class="node-side" />
      <rect x="0" y="8" width="198" height="42" class="node-box" />
      <text x="99" y="28" class="node-text">nosql-reactive</text>
      <text x="99" y="42" class="node-sub">(:8082, WebFlux)</text>
    </g>
  </g>

  <!-- ================= 2. CENTRAL / DATABASE TIER ================= -->

  <!-- postgres -->
  <g transform="translate(530, 190)">
    <path d="M 0,16 L 0,50 A 70,16 0 0,0 140,50 L 140,16 Z" class="db-cylinder" />
    <ellipse cx="70" cy="16" rx="70" ry="14" class="db-top" />
    <ellipse cx="70" cy="50" rx="70" ry="14" fill="none" stroke="#000000" stroke-width="1.5" />
    <text x="70" y="38" class="node-text">postgres</text>
    <text x="70" y="52" class="node-sub">(:5432)</text>
  </g>

  <!-- redis -->
  <g transform="translate(550, 290)">
    <path d="M 0,13 L 0,42 A 52,13 0 0,0 104,42 L 104,13 Z" class="db-cylinder" />
    <ellipse cx="52" cy="13" rx="52" ry="11" class="db-top" />
    <ellipse cx="52" cy="42" rx="52" ry="11" fill="none" stroke="#000000" stroke-width="1.5" />
    <text x="52" y="33" class="node-text">redis</text>
    <text x="52" y="46" class="node-sub">(:6379, keš)</text>
  </g>

  <!-- MongoDB Replika Set -->
  <g transform="translate(470, 380)">
    <rect width="290" height="250" rx="3" class="group-box" />
    <text x="145" y="28" class="title-group" text-anchor="middle">MongoDB replika set (rs0)</text>

    <!-- mongo1 -->
    <g transform="translate(70, 45)">
      <polygon points="0,7 10,0 155,0 145,7" class="node-top" />
      <polygon points="145,7 155,0 155,38 145,45" class="node-side" />
      <rect x="0" y="7" width="145" height="38" class="node-box" />
      <text x="72" y="25" class="node-text">mongo1 <tspan font-size="10" font-weight="normal">(primary)</tspan></text>
      <text x="72" y="38" class="node-sub">(:27017)</text>
    </g>

    <!-- mongo2 -->
    <g transform="translate(70, 115)">
      <polygon points="0,7 10,0 155,0 145,7" class="node-top" />
      <polygon points="145,7 155,0 155,38 145,45" class="node-side" />
      <rect x="0" y="7" width="145" height="38" class="node-box" />
      <text x="72" y="25" class="node-text">mongo2 <tspan font-size="10" font-weight="normal">(secondary)</tspan></text>
      <text x="72" y="38" class="node-sub">(:27018)</text>
    </g>

    <!-- mongo3 -->
    <g transform="translate(70, 185)">
      <polygon points="0,7 10,0 155,0 145,7" class="node-top" />
      <polygon points="145,7 155,0 155,38 145,45" class="node-side" />
      <rect x="0" y="7" width="145" height="38" class="node-box" />
      <text x="72" y="25" class="node-text">mongo3 <tspan font-size="10" font-weight="normal">(secondary)</tspan></text>
      <text x="72" y="38" class="node-sub">(:27019)</text>
    </g>

    <!-- Replication arrows -->
    <line x1="147" y1="90" x2="147" y2="117" stroke="#000000" stroke-width="1.4" stroke-dasharray="3,3" marker-end="url(#arrow)" />
    <line x1="147" y1="160" x2="147" y2="187" stroke="#000000" stroke-width="1.4" stroke-dasharray="3,3" marker-end="url(#arrow)" />
  </g>

  <!-- mongo-init -->
  <g transform="translate(510, 655)">
    <polygon points="0,6 9,0 170,0 161,6" class="node-top" />
    <polygon points="161,6 170,0 170,32 161,38" class="node-side" />
    <rect x="0" y="6" width="161" height="32" class="node-box" />
    <text x="80" y="25" class="node-text">mongo-init</text>
  </g>
  <line x1="595" y1="655" x2="595" y2="633" class="line-dash" marker-end="url(#arrow-open)" />
  <text x="605" y="649" class="label-text">inicijalizacija rs0</text>


  <!-- ================= 3. FLOW LINES (Backends to DBs) ================= -->
  <!-- relational to postgres -->
  <path d="M 345 180 L 530 222" class="line-flow" marker-end="url(#arrow)" />

  <!-- nosql-blocking to redis -->
  <path d="M 345 240 L 550 312" class="line-flow" marker-end="url(#arrow)" />

  <!-- nosql-reactive to redis -->
  <path d="M 345 325 L 550 320" class="line-flow" marker-end="url(#arrow)" />

  <!-- nosql-blocking to MongoDB -->
  <path d="M 345 265 L 470 445" class="line-flow" marker-end="url(#arrow)" />

  <!-- nosql-reactive to MongoDB -->
  <path d="M 345 345 L 470 520" class="line-flow" marker-end="url(#arrow)" />


  <!-- ================= 4. PRAĆENJE METRIKA ================= -->
  <g transform="translate(55, 430)">
    <rect width="380" height="170" rx="3" class="group-box" />
    <text x="190" y="30" class="title-group" text-anchor="middle">Praćenje metrika</text>

    <!-- grafana -->
    <g transform="translate(22, 52)">
      <polygon points="0,6 9,0 100,0 91,6" class="node-top" />
      <polygon points="91,6 100,0 100,42 91,48" class="node-side" />
      <rect x="0" y="6" width="91" height="42" class="node-box" />
      <text x="45" y="26" class="node-text">grafana</text>
      <text x="45" y="40" class="node-sub">(:3001)</text>
    </g>

    <!-- prometheus -->
    <g transform="translate(150, 52)">
      <polygon points="0,6 9,0 115,0 106,6" class="node-top" />
      <polygon points="106,6 115,0 115,42 106,48" class="node-side" />
      <rect x="0" y="6" width="106" height="42" class="node-box" />
      <text x="53" y="26" class="node-text">prometheus</text>
      <text x="53" y="40" class="node-sub">(:9090)</text>
    </g>

    <!-- eksporteri -->
    <g transform="translate(22, 110)">
      <rect width="335" height="44" rx="2" class="node-box" />
      <text x="167" y="18" class="node-text">eksporteri (Prometheus agenti)</text>
      <text x="167" y="34" class="node-sub">mongodb-exporter (:9216), redis-exporter (:9121), postgres-exporter (:9187), cAdvisor (:8085)</text>
    </g>

    <!-- internal flow: exporters -> prometheus, prometheus -> grafana -->
    <line x1="203" y1="100" x2="203" y2="112" class="line-flow" marker-end="url(#arrow)" />
    <line x1="113" y1="76" x2="148" y2="76" class="line-flow" marker-end="url(#arrow)" />
  </g>


  <!-- ================= 5. FRONTEND & DATA-GENERATOR ================= -->
  <!-- frontend -->
  <g transform="translate(55, 720)">
    <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
    <polygon points="180,7 190,0 190,43 180,50" class="node-side" />
    <rect x="0" y="7" width="180" height="43" class="node-box" />
    <text x="90" y="27" class="node-text">frontend (React)</text>
    <text x="90" y="41" class="node-sub">(:3000, Nginx proxy)</text>
  </g>

  <!-- frontend -> servers: route up the gap at x=470 (between metrics and MongoDB) -->
  <path d="M 150 720 L 150 690 L 470 690 L 470 78 L 200 78 L 200 85" class="line-flow" marker-end="url(#arrow)" />

  <!-- data-generator -->
  <g transform="translate(285, 720)">
    <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
    <polygon points="180,7 190,0 190,43 180,50" class="node-side" />
    <rect x="0" y="7" width="180" height="43" class="node-box" />
    <text x="90" y="27" class="node-text">data-generator</text>
    <text x="90" y="41" class="node-sub">(popunjavanje baza)</text>
  </g>

  <!-- data-generator -> databases: route through gap and up to mongo-init -->
  <path d="M 380 720 L 380 665 L 595 665 L 595 695" class="line-flow" marker-end="url(#arrow)" />
  <text x="240" y="685" class="label-text">seed (Mongo / Postgres)</text>


  <!-- ================= 6. EXPERIMENT-RUNNER & VOLUMES ================= -->
  <g transform="translate(830, 85)">
    <rect width="540" height="860" rx="3" class="group-box" />
    <text x="270" y="32" class="title-group" text-anchor="middle">Eksperimentalni pokretač (Test Harness)</text>

    <!-- experiment-runner container -->
    <g transform="translate(35, 55)">
      <rect width="280" height="420" rx="2" class="node-box" />
      <rect width="280" height="38" rx="2" class="node-top" />
      <text x="140" y="24" class="node-text">experiment-runner</text>
      <text x="140" y="52" class="node-sub">(:8090, orkestrator testova)</text>

      <!-- k6 engine -->
      <g transform="translate(45, 80)">
        <polygon points="0,0 110,0 122,12 122,65 0,65" fill="#f8fafc" stroke="#000000" stroke-width="1.2"/>
        <polygon points="110,0 110,12 122,12" fill="#e2e8f0" stroke="#000000" stroke-width="1.2"/>
        <text x="61" y="34" class="node-text">k6 engine</text>
        <text x="61" y="50" class="node-sub">(loadtest izvršilac)</text>
      </g>

      <!-- chaos scripts -->
      <g transform="translate(35, 175)">
        <polygon points="0,0 185,0 197,12 197,65 0,65" fill="#f8fafc" stroke="#000000" stroke-width="1.2"/>
        <polygon points="185,0 185,12 197,12" fill="#e2e8f0" stroke="#000000" stroke-width="1.2"/>
        <text x="98" y="30" class="node-text">skripte za ubacivanje</text>
        <text x="98" y="46" class="node-text">kvarova (reset skupa podataka)</text>
      </g>

      <!-- Docker socket access -->
      <g transform="translate(35, 270)">
        <rect width="195" height="46" rx="2" fill="#f4f4f5" stroke="#000000" stroke-width="1.2" />
        <text x="97" y="20" class="node-text">docker.sock</text>
        <text x="97" y="36" class="node-sub">(kontrola kontejnera)</text>
      </g>
    </g>

    <!-- Volumes on the host -->
    <g transform="translate(345, 130)">
      <path d="M 0,0 L 50,0 L 62,12 L 150,12 L 150,50 L 0,50 Z" class="folder-tab" />
      <text x="75" y="28" class="node-text">loadtests/</text>
      <text x="75" y="43" class="node-sub">(bind volumen :ro)</text>
    </g>
    <line x1="342" y1="155" x2="317" y2="155" class="line-dash" marker-end="url(#arrow-open)" />

    <g transform="translate(345, 240)">
      <path d="M 0,0 L 50,0 L 62,12 L 150,12 L 150,50 L 0,50 Z" class="folder-tab" />
      <text x="75" y="28" class="node-text">scripts/</text>
      <text x="75" y="43" class="node-sub">(bind volumen :ro)</text>
    </g>
    <line x1="342" y1="265" x2="317" y2="265" class="line-dash" marker-end="url(#arrow-open)" />

    <g transform="translate(345, 350)">
      <path d="M 0,0 L 50,0 L 62,12 L 150,12 L 150,50 L 0,50 Z" class="folder-tab" />
      <text x="75" y="28" class="node-text">results/</text>
      <text x="75" y="43" class="node-sub">(bind volumen :rw)</text>
    </g>
    <line x1="315" y1="375" x2="342" y2="375" class="line-dash" marker-end="url(#arrow-open)" />

    <!-- Explanation Box -->
    <g transform="translate(35, 510)">
      <rect width="470" height="320" rx="3" fill="#ffffff" stroke="#000000" stroke-width="1.2" />
      <text x="235" y="28" class="node-text" font-size="12.5">Uloga komponente experiment-runner:</text>
      <line x1="22" y1="40" x2="448" y2="40" stroke="#cbd5e1" stroke-width="1.2" />

      <g transform="translate(22, 62)" class="explain-text">
        <text y="0" font-weight="bold">1. Generisanje sintetičkog opterećenja:</text>
        <text y="18">Izvršava k6 skripte ka izabranom serverskom servisu</text>
        <text y="34">(scenariji S1 do S4, do 2 virtuelna korisnika).</text>

        <text y="66" font-weight="bold">2. Upravljanje skupom podataka (S4):</text>
        <text y="84">Preko docker.sock pristupa vraća jednak početni skup</text>
        <text y="100">podataka prije svake serije mjerenja.</text>

        <text y="132" font-weight="bold">3. Prikupljanje telemetrije:</text>
        <text y="150">Šalje upite Prometheus serveru (:9090) za potrošnju</text>
        <text y="166">CPU, memorije i odziva tokom testa.</text>

        <text y="198" font-weight="bold">4. Zapisivanje konačnih izveštaja:</text>
        <text y="216">Upisuje sirove JSON rezultate u trajni bind</text>
        <text y="232">volumen results/.</text>
      </g>
    </g>

  </g>

  <!-- ================= 7. CRITICAL LOADTEST ARROW (runner -> backends) ================= -->
  <!-- Route along the gap between runner and MongoDB at x=800, then above all groups at y=65 -->
  <path d="M 830 205 L 800 205 L 800 65 L 345 65 L 345 85" class="line-flow" stroke-width="2.2" marker-end="url(#arrow)" />
  <rect x="430" y="54" width="290" height="22" fill="#ffffff" stroke="#000000" stroke-width="1" />
  <text x="575" y="70" class="label-text" font-weight="bold" text-anchor="middle">k6 HTTP sintetičko opterećenje (S1–S4)</text>

  <!-- Runner to Prometheus metrics query: route below MongoDB via x=460 gap -->
  <path d="M 830 500 L 810 500 L 810 655 L 460 655 L 460 524 L 320 524" class="line-flow" stroke-dasharray="4,4" marker-end="url(#arrow)" />
  <text x="650" y="644" class="label-text" font-style="italic">Prometheus API upit za metrike</text>

</svg>

</body>
</html>
"""

temp_html_path = os.path.abspath("temp_arch_schema.html")
temp_png_path = os.path.abspath("temp_arch_screenshot.png")
output_jpg_path = os.path.abspath("arhitektura_sistema_klikmarket.jpg")
docs_jpg_path = os.path.abspath("docs/arhitektura_sistema_klikmarket.jpg")
artifact_jpg_path = os.path.abspath(r"C:\Users\Ivana\.gemini\antigravity\brain\b10f1e45-d7c5-4cda-a792-4d82f85cdd80\arhitektura_sistema_klikmarket.jpg")

with open(temp_html_path, "w", encoding="utf-8") as f:
    f.write(html_content)

options = Options()
options.add_argument("--headless=new")
options.add_argument("--disable-gpu")
options.add_argument("--window-size=1500,1100")
options.add_argument("--hide-scrollbars")
options.add_argument("--force-device-scale-factor=3")

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

# Blaga oštrica radi bolje čitljivosti nakon JPEG kompresije
rgb_img = rgb_img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=110, threshold=3))

rgb_img.save(output_jpg_path, "JPEG", quality=100, subsampling=0)
rgb_img.save(docs_jpg_path, "JPEG", quality=100, subsampling=0)
if os.path.isdir(os.path.dirname(artifact_jpg_path)):
    rgb_img.save(artifact_jpg_path, "JPEG", quality=100, subsampling=0)

if os.path.exists(temp_html_path):
    os.remove(temp_html_path)
if os.path.exists(temp_png_path):
    os.remove(temp_png_path)

print("Architecture JPG generated successfully!")
