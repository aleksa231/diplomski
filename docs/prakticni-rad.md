<!-- Tekst poglavlja "5. Prakticni rad" za diplomski rad. Identican je RTF dokumentu
     "Prakticni rad - KlikMarket.rtf" na Desktopu, iz kog se kopira u Word. -->

## 5. PRAKTIČNI RAD

U praktičnom dijelu rada realizovan je distribuirani softverski sistem za upoređivanje performansi NoSQL i relacionih baza podataka na primjeru web prodavnice. Realizovan je prototip aplikacije „KlikMarket“ — web prodavnica raznovrsne robe (laptopi, telefoni, odjeća, prehrambeni proizvodi i knjige) — koja je razvijena u dvije logičke cijeline: prodavnicu namijenjenu kupcima i eksperimentalnu konzolu namijenjenu istraživaču. U sklopu prodavnice omogućen je pregled kataloga proizvoda, pretraga i filtriranje po kategoriji, cijeni i ocjeni, pregled detalja proizvoda sa preporukama sličnih proizvoda, upravljanje korpom, kreiranje narudžbi, pregled istorije narudžbi, pisanje recenzija i pregled popularnih proizvoda. U sklopu eksperimentalne konzole omogućeno je pokretanje pet definisanih scenarija mjerenja (fleksibilnost šeme, keširanje i niska latencija, propusnost pisanja, reaktivni naspram blokirajućeg pristupa te analitički upiti), podešavanje parametara eksperimenta (broj virtuelnih klijenata, trajanje mjerenja, broj ponavljanja i veličina skupa podataka), praćenje napretka mjerenja u realnom vremenu, pregled i upoređivanje rezultata i izvoz rezultata. Sistem je namijenjen programerima i softverskim arhitektima koji biraju bazu podataka za konkretan projekat, a koji na osnovu izmjerenih rezultata mogu da donesu objektivnu odluku umjesto da se oslanjaju na opšte preporuke. Korisnički interfejs je u cijelosti realizovan na srpskom jeziku, sa nazivima sistema i metrika prilagođenim čitaocu koji nije upoznat sa tehničkim detaljima.

Aplikacija je razvijena u programskom jeziku Java (verzija 21) korištenjem radnog okvira Spring Boot (verzija 3.4). Realizovane su četiri uporedive serverske usluge: blokirajuća usluga nad MongoDB bazom podataka razvijena korištenjem Spring MVC radnog okvira, reaktivna usluga nad MongoDB bazom podataka razvijena korištenjem Spring WebFlux radnog okvira i reaktivnog MongoDB drajvera, te relaciona usluga nad PostgreSQL bazom podataka razvijena korištenjem Spring Data JPA pristupa za pristup podacima, koja podržava i normalizovanu šemu i JSONB oblik čuvanja podataka. Za keširanje podataka korišten je Redis, a za generisanje opterećenja alat k6. Korisnički interfejs je razvijen u React biblioteci uz TypeScript jezik, pri čemu su za izgradnju korišteni Vite alat i Tailwind CSS radni okvir. Sve komponente sistema su zatvorene u Docker kontejnere i orkestrirane pomoću Docker Compose alata, dok se metrike o iskorištenosti resursa prikupljaju pomoću alata Prometheus i Grafana.

Sistem je razvijen kao skup mikrouservisa koji međusobno komuniciraju preko REST API-ja. Da bi poređenje baza podataka bilo pošteno, sve serverske usluge realizuju isti API ugovor definisan u zajedničkom modulu sa identičnim klasama za prijenos podataka, pa klijenti i alat za opterećenje upućuju zahtjeve istog oblika i istog poslovnog značenja svakom sistemu. Prije svakog mjerenja vraća se kanonski skup podataka: deterministički generator podataka sa fiksnim sjemenom briše i ponovo ubacuje identične podatke u sve baze, čime se obezbjeđuje da se sistemi porede na istim podacima. Pošto integritet mjerenja zahtijeva da se eksperimenti pokreću jedan po jedan, usluga za koordinaciju eksperimenata odbija novi zahtjev dok prethodni eksperiment traje. Period zagrijavanja sistema prije mjerenja se ne računa u rezultat, a svako mjerenje se ponavlja više puta radi smanjenja uticaja slučnih fluktuacija. Rezultati mjerenja se trajno upisuju kao JSON dokumenti iz kojih se stanje konzole ponovo učitava nakon restarta sistema.

### 5.1 Šeme i dijagrami

U ovom paragrafu dat je prikaz sljedećih dijagrama:

- dijagram šeme relacione baze podataka,

- dijagram dokument modela NoSQL baze podataka,

- dijagram slučajeva upotrebe,

- dijagram komponenti sistema,

- sekvencioni dijagram toka eksperimenta, i

- dijagram razmještaja.

#### 5.1.1 Dijagram šeme relacione baze podataka

Šema relacione baze podataka PostgreSQL koja opisuje strukturu podataka, veze između podataka, te ograničenja kojima se štiti integritet podataka u bazi podataka predstavljena je dijagramom šeme relacione baze podataka prikazanom na slici 5.1. Šema se sastoji od tabela category (kategorije proizvoda), attribute_definition (definicije atributa koji važe za pojedinu kategoriju), product (proizvodi), product_attribute_value (vrijednosti atributa pojedinačnog proizvoda), product_jsonb (isti proizvodi u JSON obliku), orders (narudžbe), order_item (stavke narudžbi), order_status_history (istorijat promjene statusa narudžbe), cart (korpe), cart_item (stavke korpe), review (recenzije proizvoda) i product_view_count (brojač pregleda proizvoda). Jedna kategorija može da sadrži više proizvoda i da definiše više atributa, pri čemu vrijednost svakog atributa pripada tačno jednom proizvodu, čime relaciona šema obrazcem „entitet–atribut–vrijednost“ postiže fleksibilnost koju dokument model ima prirođeno. Narudžba i korpa se sastoje od više stavki, a stavka narudžbe referencira jedan proizvod. Integritet podataka štiti se primarnim i stranim ključevima, dok tabela product_jsonb čuva iste proizvode u jednoj JSON koloni, čime je u okviru iste baze obezbijeđen i dokument orijentisan oblik čuvanja podataka.

> **[Ovdje umetnuti sliku – dijagram šeme relacione baze podataka]**

_Slika 5.1 - Dijagram šeme relacione baze podataka aplikacije „KlikMarket“_

#### 5.1.2 Dijagram dokument modela NoSQL baze podataka

Dokument model NoSQL baze podataka MongoDB koji opisuje strukturu dokumenata i ugniježdenih podataka predstavljen je dijagramom dokument modela prikazanim na slici 5.2. Podaci su smješteni u tri kolekcije: products (proizvodi), orders (narudžbe) i carts (korpe). Dokument proizvoda sadrži identifikator, jedinstvenu šifru, naziv, kategoriju, cijenu, stanje zaliha, slobodne atribute kao parove ključ–vrijednost čiji skup zavisi od kategorije proizvoda (npr. memorija i kapacitet diska za laptop ili veličina i boja za odjeću) i ugniježene recenzije sa brojem recenzija i prosječnom ocjenom. Dokument narudžbe sadrži podatke o kupcu, ukupan iznos, trenutni status i ugniježene nizove stavki narudžbe i istorijata promjene statusa, pri čemu su naziv i cijena proizvoda u stavci denormalizovani, tj. prekopirani u trenutku kupovine. Za razliku od relacione šeme, između kolekcija ne postoje veze sa stranim ključevima: sve podatke potrebne za jedno čitanje dokument sadrži u sebi, pa se čitanje proizvoda ili narudžbe obavlja bez spajanja podataka iz više izvora. Upravo ta razlika u modelovanju podataka predstavlja predmet poređenja u praktičnom dijelu rada.

> **[Ovdje umetnuti sliku – dijagram dokument modela NoSQL baze podataka]**

_Slika 5.2 - Dijagram dokument modela NoSQL baze podataka aplikacije „KlikMarket“_

#### 5.1.3 Dijagram slučajeva upotrebe

Na slici 5.3 prikazan je dijagram slučajeva upotrebe aplikacije „KlikMarket“. Dijagram slučajeva upotrebe prikazuje interakciju korisnika sa sistemom odnosno odnos korisnika i različitih načina korištenja sistema koji uključuju korisnika. Akteri su kupac, koji pretražuje i filtrira katalog, pregleda detalje proizvoda, upravlja korpom, kreira narudžbe, pregleda istoriju svojih narudžbi i piše recenzije, i istraživač, koji bira scenario mjerenja, podešava parametre eksperimenta, prati napredak mjerenja u realnom vremenu, pregleda i upoređuje rezultate i izvozi ih iz sistema. Korespondirajući ekrani koji odgovaraju navedenim slučajevima upotrebe biće opisani u nastavku ove glave.

> **[Ovdje umetnuti sliku – dijagram slučajeva upotrebe]**

_Slika 5.3 - Dijagram slučajeva upotrebe aplikacije „KlikMarket“_

#### 5.1.4 Dijagram komponenti sistema

Dijagram komponenti prikazuje statičku strukturu sistema, odnosno softverske komponente i njihove zavisnosti, te je prikazan na slici 5.4. Komponenta frontend je React aplikacija koja sadrži prodavnicu i eksperimentalnu konzolu. Usluga experiment-runner koordinira eksperimentima: pokreće alat k6, primjenjuje podešavanja ciljnih sistema (npr. uključivanje i isključivanje keša), prikuplja metrike i upisuje rezultate mjerenja. Uporedive serverske usluge su nosql-blocking, nosql-reactive, nosql-blocking-w1 (sa nižim nivoom potvrde upisa), relational i relational-jsonb, koje sve zavise od zajedničkog modula common sa dijeljenim API ugovorom. Komponenta stub-service simulira eksternu uslugu sa konfigurisanim kašnjenjem odziva, čime se modeluje zavisnost sistema od spoljnjeg servisa, dok data-generator deterministički generiše i ubacuje kanonski skup podataka. Infrastrukturu čine MongoDB replika set od tri čvora, PostgreSQL i Redis kontejneri te Prometheus i Grafana za prikupljanje i pregled metrika.

> **[Ovdje umetnuti sliku – dijagram komponenti sistema]**

_Slika 5.4 - Dijagram komponenti sistema „KlikMarket“_

#### 5.1.5 Sekvencioni dijagram toka eksperimenta

Na slici 5.5 prikazan je sekvencioni dijagram toka jednog eksperimenta. Istraživač u konzoli bira scenario i parametre mjerenja, čime frontend upućuje zahtjev usluzi experiment-runner. Runner najprije vraća kanonski skup podataka pozivom komponente data-generator, koja briše i ponovo ubacuje identične podatke u MongoDB i PostgreSQL baze. Zatim se za svaki ciljni sistem redom primjenjuju podešavanja sistema (npr. isključivanje keša ili nivo potvrde upisa), izvodi se period zagrijavanja koji se ne računa u rezultat te se izvršavaju ponavljanja mjerenja u kojima alat k6 generiše zahtjeve virtuelnih klijenata prema ciljnom sistemu, pri čemu se preko Prometheusa prikupljaju i metrike iskorištenosti resursa. Na kraju runner upisuje rezultat kao JSON dokument, a konzola prikazuje izvještaj sa tabelama i grafovima rezultata.

> **[Ovdje umetnuti sliku – sekvencioni dijagram toka eksperimenta]**

_Slika 5.5 - Sekvencioni dijagram toka eksperimenta_

#### 5.1.6 Dijagram razmještaja

Dijagram razmještaja modeluje raspored komponenti u eksploataciji sistema. Na njemu su identifikovani čvorovi koji mogu da sadrže softverske artefakte kao i veze između čvorova odnosno artefakata. Sistem „KlikMarket“ se u cijelosti izvršava u Docker kontejnerima na jednom fizičkom računaru: tri čvora MongoDB replika seta (mongo1, mongo2 i mongo3) sa servisom inicijalizacije, PostgreSQL, Redis, pet uporedivih serverskih usluga, stub-service, experiment-runner, frontend i data-generator, te alati za praćenje (Prometheus, Grafana, eksporteri za MongoDB, Redis i PostgreSQL te cAdvisor). Direktorijumi sa skriptama za opterećenje, skriptama za ubacivanje kvarova i rezultatima mjerenja su u kontejnere povezani kao takozvani bind volumeni, pa se izmjene skripti vide odmah, a sačuvani rezultati preživljavaju restart kontejnera. Dijagram razmještaja sistema prikazan je na slici 5.6.

> **[Ovdje umetnuti sliku – dijagram razmještaja sistema]**

_Slika 5.6 - Dijagram razmještaja sistema „KlikMarket“_
