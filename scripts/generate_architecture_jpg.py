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
    padding: 25px;
  }
  svg {
    background-color: #ffffff;
  }
  .host-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.5;
  }
  .group-box {
    fill: #fcfcfd;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .node-box {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.1;
  }
  .node-top {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.1;
  }
  .node-side {
    fill: #e4e4e7;
    stroke: #000000;
    stroke-width: 1.1;
  }
  .db-cylinder {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .db-top {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.2;
  }
  .folder-tab {
    fill: #f4f4f5;
    stroke: #000000;
    stroke-width: 1.1;
  }
  .folder-body {
    fill: #ffffff;
    stroke: #000000;
    stroke-width: 1.1;
  }
  .line-flow {
    stroke: #000000;
    stroke-width: 1.2;
    fill: none;
  }
  .line-dash {
    stroke: #000000;
    stroke-width: 1.2;
    stroke-dasharray: 4,4;
    fill: none;
  }
  .title-main {
    font-size: 13.5px;
    font-weight: bold;
    fill: #000000;
  }
  .title-group {
    font-size: 12px;
    font-weight: bold;
    fill: #000000;
  }
  .node-text {
    font-size: 11px;
    font-weight: bold;
    fill: #000000;
    text-anchor: middle;
  }
  .node-sub {
    font-size: 9.5px;
    fill: #444444;
    text-anchor: middle;
  }
  .label-text {
    font-size: 9.5px;
    fill: #000000;
    background-color: #ffffff;
  }
</style>
</head>
<body>

<svg width="1280" height="920" viewBox="0 0 1280 920">
  <defs>
    <!-- Standard arrow marker -->
    <marker id="arrow" viewBox="0 0 16 16" refX="14" refY="8" markerWidth="9" markerHeight="9" orient="auto">
      <path d="M 2 3 L 14 8 L 2 13 Z" fill="#000000" />
    </marker>
    <!-- Open arrow for dashed lines -->
    <marker id="arrow-open" viewBox="0 0 16 16" refX="14" refY="8" markerWidth="9" markerHeight="9" orient="auto">
      <path d="M 2 2 L 14 8 L 2 14" fill="none" stroke="#000000" stroke-width="1.4" />
    </marker>
  </defs>

  <!-- ================= DOCKER HOST BOUNDARY ================= -->
  <rect x="30" y="25" width="1220" height="870" rx="4" class="host-box" />
  <text x="640" y="52" class="title-main" text-anchor="middle">Fizički računar (Docker host)</text>


  <!-- ================= 1. UPOREDIVE SERVERSKE USLUGE ================= -->
  <g transform="translate(60, 80)">
    <rect width="250" height="380" rx="3" class="group-box" />
    <text x="125" y="26" class="title-group" text-anchor="middle">Uporedive serverske usluge</text>
    
    <!-- 1.1 relational -->
    <g transform="translate(30, 45)">
      <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
      <polygon points="180,7 190,0 190,38 180,45" class="node-side" />
      <rect x="0" y="7" width="180" height="38" class="node-box" />
      <text x="90" y="26" class="node-text">relational</text>
      <text x="90" y="38" class="node-sub">(:8083, EAV model)</text>
    </g>

    <!-- 1.2 relational-jsonb -->
    <g transform="translate(30, 110)">
      <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
      <polygon points="180,7 190,0 190,38 180,45" class="node-side" />
      <rect x="0" y="7" width="180" height="38" class="node-box" />
      <text x="90" y="26" class="node-text">relational-jsonb</text>
      <text x="90" y="38" class="node-sub">(:8087, JSONB model)</text>
    </g>

    <!-- 1.3 nosql-blocking -->
    <g transform="translate(30, 175)">
      <polygon points="0,7 10,0 190,0 190,38 180,45" class="node-side" />
      <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
      <rect x="0" y="7" width="180" height="38" class="node-box" />
      <text x="90" y="26" class="node-text">nosql-blocking</text>
      <text x="90" y="38" class="node-sub">(:8081, MVC + Mongo/Redis)</text>
    </g>

    <!-- 1.4 nosql-blocking-w1 -->
    <g transform="translate(30, 240)">
      <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
      <polygon points="180,7 190,0 190,38 180,45" class="node-side" />
      <rect x="0" y="7" width="180" height="38" class="node-box" />
      <text x="90" y="26" class="node-text">nosql-blocking-w1</text>
      <text x="90" y="38" class="node-sub">(:8086, w=1 commit)</text>
    </g>

    <!-- 1.5 nosql-reactive -->
    <g transform="translate(30, 305)">
      <polygon points="0,7 10,0 190,0 180,7" class="node-top" />
      <polygon points="180,7 190,0 190,38 180,45" class="node-side" />
      <rect x="0" y="7" width="180" height="38" class="node-box" />
      <text x="90" y="26" class="node-text">nosql-reactive</text>
      <text x="90" y="38" class="node-sub">(:8082, WebFlux)</text>
    </g>
  </g>


  <!-- ================= 2. CENTRAL / DATABASE TIER ================= -->

  <!-- 2.1 stub-service -->
  <g transform="translate(490, 85)">
    <polygon points="0,7 10,0 150,0 140,7" class="node-top" />
    <polygon points="140,7 150,0 150,38 140,45" class="node-side" />
    <rect x="0" y="7" width="140" height="38" class="node-box" />
    <text x="70" y="26" class="node-text">stub-service</text>
    <text x="70" y="38" class="node-sub">(:8099, S5 latencija)</text>
  </g>

  <!-- 2.2 postgres (PostgreSQL 16) -->
  <g transform="translate(500, 155)">
    <path d="M 0,15 L 0,45 A 60,15 0 0,0 120,45 L 120,15 Z" class="db-cylinder" />
    <ellipse cx="60" cy="15" rx="60" ry="12" class="db-top" />
    <ellipse cx="60" cy="45" rx="60" ry="12" fill="none" stroke="#000000" stroke-width="1.2" />
    <text x="60" y="35" class="node-text">postgres</text>
    <text x="60" y="46" class="node-sub">(:5432)</text>
  </g>

  <!-- 2.3 redis (Redis 7.2) -->
  <g transform="translate(515, 235)">
    <path d="M 0,12 L 0,40 A 45,12 0 0,0 90,40 L 90,12 Z" class="db-cylinder" />
    <ellipse cx="45" cy="12" rx="45" ry="10" class="db-top" />
    <ellipse cx="45" cy="40" rx="45" ry="10" fill="none" stroke="#000000" stroke-width="1.2" />
    <text x="45" y="30" class="node-text">redis</text>
    <text x="45" y="41" class="node-sub">(:6379, keš)</text>
  </g>

  <!-- 2.4 MongoDB Replika Set Cluster -->
  <g transform="translate(440, 305)">
    <rect width="240" height="235" rx="3" class="group-box" />
    <text x="120" y="24" class="title-group" text-anchor="middle">MongoDB replika set (rs0)</text>

    <!-- mongo1 -->
    <g transform="translate(50, 40)">
      <polygon points="0,6 8,0 148,0 140,6" class="node-top" />
      <polygon points="140,6 148,0 148,34 140,40" class="node-side" />
      <rect x="0" y="6" width="140" height="34" class="node-box" />
      <text x="70" y="23" class="node-text">mongo1 <tspan font-size="9" font-weight="normal">(primary)</tspan></text>
      <text x="70" y="34" class="node-sub">(:27017)</text>
    </g>

    <!-- mongo2 -->
    <g transform="translate(50, 105)">
      <polygon points="0,6 8,0 148,0 140,6" class="node-top" />
      <polygon points="140,6 148,0 148,34 140,40" class="node-side" />
      <rect x="0" y="6" width="140" height="34" class="node-box" />
      <text x="70" y="23" class="node-text">mongo2 <tspan font-size="9" font-weight="normal">(secondary)</tspan></text>
      <text x="70" y="34" class="node-sub">(:27018)</text>
    </g>

    <!-- mongo3 -->
    <g transform="translate(50, 170)">
      <polygon points="0,6 8,0 148,0 140,6" class="node-top" />
      <polygon points="140,6 148,0 148,34 140,40" class="node-side" />
      <rect x="0" y="6" width="140" height="34" class="node-box" />
      <text x="70" y="23" class="node-text">mongo3 <tspan font-size="9" font-weight="normal">(secondary)</tspan></text>
      <text x="70" y="34" class="node-sub">(:27019)</text>
    </g>

    <!-- Replication arrows inside Replica Set -->
    <line x1="120" y1="80" x2="120" y2="108" stroke="#000000" stroke-width="1.2" stroke-dasharray="3,3" marker-end="url(#arrow)" />
    <line x1="120" y1="145" x2="120" y2="173" stroke="#000000" stroke-width="1.2" stroke-dasharray="3,3" marker-end="url(#arrow)" />
  </g>

  <!-- mongo-init container -->
  <g transform="translate(470, 560)">
    <polygon points="0,5 7,0 147,0 140,5" class="node-top" />
    <polygon points="140,5 147,0 147,27 140,32" class="node-side" />
    <rect x="0" y="5" width="140" height="27" class="node-box" />
    <text x="70" y="22" class="node-text">mongo-init</text>
  </g>
  <line x1="540" y1="560" x2="540" y2="543" class="line-dash" marker-end="url(#arrow-open)" />
  <text x="548" y="555" class="label-text">inicijalizacija rs0</text>


  <!-- ================= 3. FLOW LINES (Backends to DBs & Stub) ================= -->
  <!-- Backends to stub-service -->
  <line x1="310" y1="105" x2="487" y2="105" class="line-flow" marker-end="url(#arrow)" />

  <!-- relational & jsonb to postgres -->
  <line x1="280" y1="150" x2="497" y2="180" class="line-flow" marker-end="url(#arrow)" />
  <line x1="280" y1="205" x2="497" y2="185" class="line-flow" marker-end="url(#arrow)" />

  <!-- Backends to redis (ISPRAVLJENO: i relaciono i nosql koriste redis) -->
  <line x1="280" y1="225" x2="512" y2="255" class="line-flow" marker-end="url(#arrow)" />
  <line x1="280" y1="275" x2="512" y2="260" class="line-flow" marker-end="url(#arrow)" />

  <!-- nosql backends to MongoDB Replica Set -->
  <line x1="280" y1="290" x2="437" y2="360" class="line-flow" marker-end="url(#arrow)" />
  <line x1="280" y1="345" x2="437" y2="385" class="line-flow" marker-end="url(#arrow)" />
  <line x1="280" y1="410" x2="437" y2="420" class="line-flow" marker-end="url(#arrow)" />


  <!-- ================= 4. PRAĆENJE METRIKA (OBSERVABILITY) ================= -->
  <g transform="translate(60, 485)">
    <rect width="360" height="150" rx="3" class="group-box" />
    <text x="180" y="24" class="title-group" text-anchor="middle">Praćenje metrika</text>

    <!-- grafana -->
    <g transform="translate(18, 45)">
      <polygon points="0,5 7,0 92,0 85,5" class="node-top" />
      <polygon points="85,5 92,0 92,37 85,42" class="node-side" />
      <rect x="0" y="5" width="85" height="37" class="node-box" />
      <text x="42" y="24" class="node-text">grafana</text>
      <text x="42" y="36" class="node-sub">(:3001)</text>
    </g>

    <!-- prometheus -->
    <g transform="translate(135, 45)">
      <polygon points="0,5 7,0 102,0 95,5" class="node-top" />
      <polygon points="95,5 102,0 102,37 95,42" class="node-side" />
      <rect x="0" y="5" width="95" height="37" class="node-box" />
      <text x="47" y="24" class="node-text">prometheus</text>
      <text x="47" y="36" class="node-sub">(:9090)</text>
    </g>

    <!-- eksporteri -->
    <g transform="translate(18, 97)">
      <rect width="325" height="40" rx="2" class="node-box" />
      <text x="162" y="16" class="node-text">eksporteri (Prometheus agenti)</text>
      <text x="162" y="30" class="node-sub">mongodb-exporter (:9216), redis-exporter (:9121), postgres-exporter (:9187), cAdvisor (:8085)</text>
    </g>

    <!-- flow inside metrics -->
    <line x1="103" y1="68" x2="132" y2="68" class="line-flow" marker-end="url(#arrow)" />
    <line x1="182" y1="87" x2="182" y2="94" class="line-flow" marker-end="url(#arrow)" />
  </g>


  <!-- ================= 5. FRONTEND & DATA-GENERATOR ================= -->
  <!-- frontend -->
  <g transform="translate(60, 665)">
    <polygon points="0,6 8,0 148,0 140,6" class="node-top" />
    <polygon points="140,6 148,0 148,36 140,42" class="node-side" />
    <rect x="0" y="6" width="140" height="36" class="node-box" />
    <text x="70" y="24" class="node-text">frontend (React)</text>
    <text x="70" y="36" class="node-sub">(:3000, Nginx proxy)</text>
  </g>
  <!-- frontend connection to services and runner -->
  <path d="M 130 665 L 130 463" class="line-flow" marker-end="url(#arrow)" />

  <!-- data-generator -->
  <g transform="translate(230, 665)">
    <polygon points="0,6 8,0 148,0 140,6" class="node-top" />
    <polygon points="140,6 148,0 148,36 140,42" class="node-side" />
    <rect x="0" y="6" width="140" height="36" class="node-box" />
    <text x="70" y="24" class="node-text">data-generator</text>
    <text x="70" y="36" class="node-sub">(popunjavanje baza)</text>
  </g>
  <!-- data-generator connection to dbs (ISPRAVLJENO!) -->
  <path d="M 370 685 L 490 685 L 490 545" class="line-flow" marker-end="url(#arrow)" />
  <text x="495" y="675" class="label-text">seed (Mongo / Postgres)</text>


  <!-- ================= 6. EXPERIMENT-RUNNER & VOLUMES ================= -->
  <g transform="translate(770, 80)">
    <rect width="440" height="780" rx="3" class="group-box" />
    <text x="220" y="30" class="title-group" text-anchor="middle">Eksperimentalni pokretač (Test Harness)</text>

    <!-- experiment-runner container -->
    <g transform="translate(30, 50)">
      <rect width="210" height="360" rx="2" class="node-box" />
      <rect width="210" height="35" rx="2" class="node-top" />
      <text x="105" y="22" class="node-text">experiment-runner</text>
      <text x="105" y="48" class="node-sub">(:8090, orkestrator testova)</text>

      <!-- Inside experiment-runner: k6 engine -->
      <g transform="translate(35, 75)">
        <polygon points="0,0 90,0 100,10 100,60 0,60" fill="#f8fafc" stroke="#000000" stroke-width="1"/>
        <polygon points="90,0 90,10 100,10" fill="#e2e8f0" stroke="#000000" stroke-width="1"/>
        <text x="50" y="32" class="node-text">k6 engine</text>
        <text x="50" y="47" class="node-sub">(loadtest izvršilac)</text>
      </g>

      <!-- Inside experiment-runner: chaos scripts -->
      <g transform="translate(25, 160)">
        <polygon points="0,0 145,0 155,10 155,60 0,60" fill="#f8fafc" stroke="#000000" stroke-width="1"/>
        <polygon points="145,0 145,10 155,10" fill="#e2e8f0" stroke="#000000" stroke-width="1"/>
        <text x="77" y="28" class="node-text">skripte za ubacivanje</text>
        <text x="77" y="42" class="node-text">kvarova (S3 failover)</text>
      </g>

      <!-- Docker socket access -->
      <g transform="translate(25, 245)">
        <rect width="155" height="42" rx="2" fill="#f4f4f5" stroke="#000000" stroke-width="0.9" />
        <text x="77" y="18" class="node-text" font-size="10">docker.sock</text>
        <text x="77" y="32" class="node-sub">(kontrola kontejnera)</text>
      </g>
    </g>

    <!-- Volumes on the host -->
    <!-- loadtests/ volume -->
    <g transform="translate(275, 100)">
      <path d="M 0,0 L 45,0 L 55,10 L 130,10 L 130,45 L 0,45 Z" class="folder-tab" />
      <text x="65" y="24" class="node-text">loadtests/</text>
      <text x="65" y="38" class="node-sub">(bind volumen :ro)</text>
    </g>
    <line x1="272" y1="125" x2="243" y2="125" class="line-dash" marker-end="url(#arrow-open)" />

    <!-- scripts/ volume -->
    <g transform="translate(275, 190)">
      <path d="M 0,0 L 45,0 L 55,10 L 130,10 L 130,45 L 0,45 Z" class="folder-tab" />
      <text x="65" y="24" class="node-text">scripts/</text>
      <text x="65" y="38" class="node-sub">(bind volumen :ro)</text>
    </g>
    <line x1="272" y1="215" x2="243" y2="215" class="line-dash" marker-end="url(#arrow-open)" />

    <!-- results/ volume -->
    <g transform="translate(275, 280)">
      <path d="M 0,0 L 45,0 L 55,10 L 130,10 L 130,45 L 0,45 Z" class="folder-tab" />
      <text x="65" y="24" class="node-text">results/</text>
      <text x="65" y="38" class="node-sub">(bind volumen :rw)</text>
    </g>
    <!-- Arrow from experiment-runner INTO results (ISPRAVLJENO!) -->
    <line x1="240" y1="305" x2="272" y2="305" class="line-dash" marker-end="url(#arrow-open)" />

    <!-- Explanation Box for Diplomska Teza -->
    <g transform="translate(30, 440)">
      <rect width="375" height="310" rx="3" fill="#ffffff" stroke="#000000" stroke-width="1" />
      <text x="187" y="25" class="node-text" font-size="11.5">Uloga komponente experiment-runner:</text>
      <line x1="20" y1="35" x2="355" y2="35" stroke="#cbd5e1" stroke-width="1" />
      
      <g transform="translate(18, 55)" font-size="10.5" fill="#1e293b">
        <text y="0" font-weight="bold">1. Generisanje sintetičkog opterećenja:</text>
        <text y="16">Izvršava k6 skripte ka izabranom backend servisu</text>
        <text y="30">(scenariji S1 do S7, do 50 virtuelnih korisnika).</text>

        <text y="60" font-weight="bold">2. Ubacivanje kontrolisanih otkaza (S3):</text>
        <text y="76">Preko docker.sock pristupa gasi primarni Mongo čvor</text>
        <text y="90">i meri vreme izbora novog primarnog čvora.</text>

        <text y="120" font-weight="bold">3. Prikupljanje telemetrije:</text>
        <text y="136">Šalje upite Prometheus serveru (:9090) za potrošnju</text>
        <text y="150">CPU, memorije i odziva tokom testa.</text>

        <text y="180" font-weight="bold">4. Zapisivanje konačnih izveštaja:</text>
        <text y="196">Upisuje sirove JSON i sumirane CSV rezultate u</text>
        <text y="210">trajni bind volumen results/.</text>
      </g>
    </g>

  </g>

  <!-- ================= 7. CRITICAL LOADTEST ARROW (runner -> backends) ================= -->
  <!-- k6 load arrow pointing to Backends (ISPRAVLJENO: najvažnija veza!) -->
  <path d="M 770 200 L 695 200 L 695 65 L 313 65 L 313 85" class="line-flow" stroke-width="1.8" marker-end="url(#arrow)" />
  <rect x="360" y="55" width="220" height="18" fill="#ffffff" stroke="#000000" stroke-width="0.8" />
  <text x="470" y="68" class="label-text" font-weight="bold" text-anchor="middle">k6 HTTP sintetičko opterećenje (S1–S7)</text>

  <!-- Runner to Prometheus metrics query -->
  <path d="M 770 420 L 730 420 L 730 655 L 423 655 L 423 580" class="line-flow" stroke-dasharray="3,3" marker-end="url(#arrow)" />
  <text x="600" y="650" class="label-text" font-style="italic">Prometheus API upit za metrike</text>

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
options.add_argument("--window-size=1380,1000")
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

print("Architecture JPG generated successfully!")
