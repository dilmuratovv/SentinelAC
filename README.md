# SentinelAC — Paper 26.2

[GitHub release va JAR yuklamasi](https://github.com/dilmuratovv/SentinelAC/releases/tag/v2.5.0-paper26.2).

**Bitta JAR: `SentinelAC-2.5.0-paper26.2.jar`. Paper 26.2 va Java 25 uchun.** Harakat, Reach, Timer, Knockback va paket tekshiruvlari ichki prediction yadrosida ishlaydi. Sentinel kanal blacklisti, crystal/anchor tezlik qoidalari va chegaralangan diagnostikani qo‘shadi.

## O‘rnatish

1. Server va eski konfiguratsiya/database fayllarining nusxasini oling. Eski Sentinel va alohida GrimAC JAR’larini `plugins/`dan chiqaring; ushbu build ichida yadro bor.
2. Build natijasidagi **faqat SentinelAC JAR**ni `plugins/`ga qo‘ying. Eski konfiguratsiyani avtomatik ko‘chirib qo‘ymang; yangi standartlar bilan solishtiring. Oldingi forkning tarix bazasi uchun avtomatik migratsiya va’da qilinmaydi.
3. Via kerak bo‘lsa, ViaVersion/ViaBackwards’ni **backend Paper serverga** o‘rnating. Sinov juftligi: **5.12.2 / 5.12.2**. Proxy’dagi ViaVersion qo‘llanmaydi.
4. Restart qiling. Konsolda `Sentinel ready: Paper 26.2` va `/sac info` natijasini tekshiring.

Barcha sozlamalar `plugins/SentinelAC/` ichida: `config.yml`, `messages.yml`, `punishments.yml`, `database.yml`, `databases/`, `discord.yml`. `sentinel.admin` boshqaruv huquqlarini beradi. Exemption huquqlari defaultda **false**; administrator bo‘lish avtomatik exemption bermaydi.

## Buyruqlar

Asosiy buyruq **`/sac`**, aliaslar **`/sentinel`**, **`/sentinelac`**. Bitta command manager ishlatiladi.

| Buyruq | Vazifa | Ruxsat |
|---|---|---|
| `/sac help` | Yadro va boshqaruv yordami | `sentinel.help` |
| `/sac info` | Build, Via va diagnostika holati | `sentinel.info` |
| `/sac alerts`, `/sac verbose` | Tekshiruv xabarlari | `sentinel.alerts`, `sentinel.verbose` |
| `/sac profile <player>` | Klient va tracking ma’lumoti | `sentinel.profile` |
| `/sac channels <player>` | Klient e’lon qilgan kanallar | `sentinel.profile` |
| `/sac recent <player>` | Oxirgi mahalliy diagnostika yozuvlari | `sentinel.history` |
| `/sac history <player>` | Bazadagi session va violation tarixi | `sentinel.history` |
| `/sac perf` | Prediction hisoblash vaqti | `sentinel.performance` |
| `/sac reload` | Sozlamalarni tekshirib qayta yuklash | `sentinel.reload` |
| `/sac version` | Haqiqiy build versiyasi | `sentinel.version` |
| `/sac log <id>`, `/sac dump` | Server diskiga diagnostika saqlash | `sentinel.log`, `sentinel.dump` |

## Qoidalar va cheklovlar

`mod-sentinel` kirishda mavjud kanallarni va keyingi kanal ro‘yxatdan o‘tishlarini tekshiradi. Blacklistdagi **exact kanal yoki namespace** aniqlansa kick qiladi. `baritone:channel` mos keladi; `notbaritone:main` mos kelmaydi. `/sac channels` o‘rnatilgan barcha modlar ro‘yxati emas: kanalini yashirgan modlarni server to‘liq aniqlay olmaydi. Blacklist ro‘yxati server siyosatiga moslab tahrirlanadi.

Crystal/anchor cheklovlari bir soniyalik siljiydigan oynadan foydalanadi. Ikkala qo‘l crystal limitini birga ishlatadi. Limitdan oshgan amal bekor qilinadi; bu o‘zicha cheat isboti yoki doimiy ban sababi emas. Eski 90 ms AutoTotem, qo‘shimcha exhaustion beradigan NoHunger va zaif event Reach/FastEat detektorlari qaytarilmagan.

`deployment/SentinelAC/punishments.yml` — **ixtiyoriy** takroriy flaglarda kick beruvchi profil. Avval staging’da tekshiring; u avtomatik o‘rnatilmaydi va doimiy ban bermaydi. Fizika tekshiruvlarining setback/cancellation himoyasi standart konfiguratsiyada saqlanadi.

ViaBackwards bilan eski klientlarning vehicle/fizika mosligi cheklangan; upstream’ning 1.21.2+ serverlar haqidagi ogohlantirishi saqlangan. Ulanish testi barcha mexanikalarning tasdig‘i emas. To‘liq native 26.2 o‘yin sessiyalari, katta online va cheat-client sinovlari alohida talab qilinadi. Nol lag, nol false positive yoki barcha cheatlarni 100% aniqlash kafolati berilmaydi.

## Manba va o‘zgarishlar

Yadro **[GrimAC](https://github.com/GrimAnticheat/Grim)** asosida manba kodidan yig‘iladi. GrimAPI ham manbadan kompilyatsiya qilinadi; alohida tayyor engine binari ko‘chirilmaydi. Asl kodlar commitga pin qilingan submodule’larda saqlanadi. Build generator port va o‘zgarishlarni ochiq ko‘rsatadi.

| Manba | Pin |
|---|---|
| Yadro v2.3.74 | `f31ba923100d9a991939d7ea24fde541d6fa2929` |
| API 1.6.0.13 | `e9ae70ad6e13af0f3dab02ac849608443fb69f8a` |
| Paper API | `26.2.build.135-stable` |

`runtime/` faqat hash bilan tekshirilgan upstream distribution ichidagi uchinchi tomon kutubxonalari va protokol assetlarini oladi. Yadro/API klasslari chiqarib tashlanadi; compatibility shim ham manbadan qayta yig‘iladi. Bu exact PacketEvents snapshot va minimallashtirilgan kutubxonalarni qayta ishlatish uchun tanlangan. Kutubxona namespace’lari va serialized-lambda descriptorlari izchil ko‘chiriladi.

Qo‘shilgan o‘zgarishlar:

- Paket/fizika tekshiruvlarini o‘chirmasdan ray hisoblashdagi vaqtinchalik vektor olib tashlandi. Natijalar 25 000 tasodifiy holat va chegaraviy holatlarda asl arifmetika bilan bit darajasida solishtiriladi. Global TPS oshishi o‘lchangan deb da’vo qilinmaydi.
- Discord navbati 256 so‘rov bilan cheklangan; bir vaqtda bitta yuborish, 60 soniya navbat muddati, rate-limit uchun ko‘pi bilan uch urinish. Network xatosi navbat boshini cheksiz ushlab turmaydi. Webhook defaultda o‘chiq va faqat administrator sozlagan manzilga yuboradi.
- Diagnostika navbati/xotira tarixi cheklangan, disk loglari hajm va son bo‘yicha aylanadi. Dump/debug fayllari lokal, ko‘pi bilan 20 fayl va har biri 2 MiB. bStats va upstream update/paste xizmatlariga avtomatik murojaat olib tashlangan.
- Kanal qoidalari immutable; noto‘g‘ri Sentinel sozlamalari reload’dan oldin rad etiladi. Buffer/log hajmini o‘zgartirish restart talab qiladi. Yadro reload xatosidan keyin konsol tekshiriladi; barcha subsystemlar uchun to‘liq transaction kafolati berilmaydi.

## Yig‘ish va tekshirish

JDK 25, Maven 3.9+, Python 3 va Git:

```sh
git clone --recurse-submodules https://github.com/dilmuratovv/SentinelAC.git
cd SentinelAC
git submodule update --init --recursive
mvn -B -ntp clean verify
python3 tools/verify_artifact.py plugin/target/SentinelAC-2.5.0-paper26.2.jar
```

Natija: `plugin/target/SentinelAC-2.5.0-paper26.2.jar`. Verifier arxiv tuzilishi, barcha backend shablonlari, Java 25 bytecode, litsenziyalar va faol klass/matnlarda eski branding qolmaganini tekshiradi.

Izolyatsiyalangan localhost server sinovi:

```sh
npm ci --prefix tools/client
python3 tools/smoke_paper.py --accept-eula --client-probes --java /path/to/jdk-25/bin/java
```

`--accept-eula`ni [Minecraft EULA](https://www.minecraft.net/eula)ni qabul qilgan bo‘lsangiz bering. Testlar `target/smoke/`da ishlaydi va production serverga ulanmaydi. Aniq qamrov: [docs/VALIDATION.md](docs/VALIDATION.md).

## Minnatdorchilik va litsenziya

Sentinel modullari: **azicoo**. **GrimAC, DefineOutside va barcha GrimAnticheat/GrimAPI mualliflariga rahmat.** Litsenziya: [GPL-3.0](LICENSE). Asl copyright va litsenziya yozuvlari submodule’larda va JAR ichidagi `META-INF/NOTICE.txt`, `META-INF/LICENSE.txt`, `META-INF/licenses/`da saqlangan. Grim nomi shu huquqiy/manba yozuvlarida qoladi; faol nomlar, buyruqlar, permissionlar va engine namespace’lari SentinelAC’ga o‘tkazilgan.
