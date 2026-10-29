# SentinelAC — Paper 26.2

SentinelAC adds channel blacklist enforcement, crystal/anchor rate policies and bounded diagnostics to the **official GrimAC engine**. The target for this release is **Paper 26.2, Java 25**.

This is a source-buildable companion plugin. Install **both GrimAC and SentinelAC**. The old modified Grim binaries and bytecode patchers are removed from the current branch; their original versions remain in Git history. Sentinel does not contain a second packet injector or copy of the engine.

## O‘rnatish

1. Server va `plugins/SentinelAC/` papkasining nusxasini oling. Eski Sentinel JAR’larini `plugins/`dan chiqarib qo‘ying; ikki Sentinel versiyasini birga ishlatmang.
2. [Rasmiy GrimAC 2.3.74 Bukkit](https://modrinth.com/plugin/grimac/version/baDrVvfo) va build natijasidagi `SentinelAC-2.4.0-paper26.2.jar`ni `plugins/`ga joylashtiring. Fabric buildini tanlamang.
3. Via kerak bo‘lsa, **ViaVersion va ViaBackwards backend Paper serverda** bo‘lsin. Grim proxy’da o‘rnatilgan ViaVersion’ni qo‘llamaydi. Sinovdagi juftlik: **5.12.2 / 5.12.2**.
4. Serverni qayta boshlang. Konsolda `Sentinel ready: Paper 26.2, GrimAC 2.3.74` chiqishi va `/sentinel info` ishlashini tekshiring.
5. Sentinel sozlamalari: `plugins/SentinelAC/config.yml`. Fizika, paketlar va yadro jazolari: `plugins/GrimAC/`. `/sentinel reload` ikkalasining natijasini tekshiradi; xatoda Sentinel’ning amaldagi snapshot’i saqlanadi. Grim reload xatosidan keyin uning konsol logini ham tekshiring.

**ViaBackwards cheklovi:** GrimAC 2.3.74, 1.21.2+ serverlarda eski klientlar transport vositalarini ishlatishi uchun upstream ogohlantirish beradi. Ulanish yoki startup testi barcha vehicle/fizika kombinatsiyalarining tasdig‘i emas. Bu release barcha eski klientlarga to‘liq mos deb da’vo qilmaydi. Ishlatiladigan client versiyalarini server nusxasida tekshiring. Hozirgi validatsiya qamrovi [docs/VALIDATION.md](docs/VALIDATION.md)da.

## Himoya chegaralari

- Harakat, Reach, Timer, Knockback va paket tekshiruvlarini Grim bajaradi. Sentinel uning private/internal klasslariga tayanmaydi va flaglarini bekor qilmaydi.
- `mod-sentinel` klient e’lon qilgan kanallarni kirishda va keyingi ro‘yxatdan o‘tishlarda tekshiradi. Exact channel yoki exact namespace mos kelganda, sozlangan bo‘lsa kick qiladi. `notbaritone:main` — `baritone` qoidasi bilan mos kelmaydi.
- Bu haqiqiy o‘rnatilgan modlar inventari emas. Kanal e’lon qilmaydigan/yashiradigan mod ko‘rinmasligi mumkin. `/sentinel channels` ham faqat e’lon qilingan kanallarni ko‘rsatadi. Blacklist — server siyosati; mod borligining kriptografik isboti emas.
- Crystal/anchor limiti **server tezlik qoidasi**: limitdan ortgan amal bekor qilinadi; buning o‘zi cheat deb avtomatik ban bermaydi. Ikkala qo‘l umumiy crystal limitini ishlatadi. Fixed bir soniyalik reset o‘rniga siljiydigan vaqt oynasi ishlatiladi.
- Eski `90 ms` AutoTotem, qo‘shimcha exhaustion beradigan NoHunger va zaif event Reach/FastEat tekshiruvlari olib tashlangan. Ular tasdiqlanmagan detektor sifatida qayta reklama qilinmaydi.
- Yadro cheklovlari, X-ray/ESP va klientda qoladigan axborot sabab barcha cheatlarni 100% to‘sish kafolati berilmaydi. Paper Anti-Xray alohida server konfiguratsiyasi sifatida ishlatiladi.

## Buyruqlar va ruxsatlar

| Buyruq | Ruxsat | Vazifa |
|---|---|---|
| `/sentinel info` | `sentinel.info` | Versiyalar, faol siyosatlar va diagnostika hisoblari |
| `/sentinel alerts` | `sentinel.alerts` | Grim va Sentinel uchun umumiy alert holati |
| `/sentinel profile <player>` | `sentinel.profile` | Haqiqiy tracking ma’lumoti; yo‘q bo‘lsa taxmin qilinmaydi |
| `/sentinel channels <player>` | `sentinel.profile` | E’lon qilingan kanallar va mos blacklist qoidasi |
| `/sentinel history <player>` | `sentinel.history` | Xotiradagi oxirgi 10 yozuv |
| `/sentinel perf` | `sentinel.performance` | Diagnostika navbati va Grim performance buyrug‘i |
| `/sentinel reload` | `sentinel.reload` | Konfiguratsiyani validatsiya qilib qayta yuklash |

Aliaslar: `/sac`, `/sentinelac`. `sentinel.admin` boshqaruv ruxsatlarini beradi, ammo exemption bermaydi. `sentinel.exempt` defaultda **false** va Grim exemption’ini ham beradi. `grim.exempt` ham Sentinel qoidalarida hurmat qilinadi.

## Log, resurs va jazo siyosati

Diagnostika navbati, so‘nggi tarix va hisoblar chegaralangan. Disk yozuvi alohida cheklangan worker’da ishlaydi. Loglar `plugins/SentinelAC/logs/violations.*.log`da hajm/son bo‘yicha aylanadi. Client matnlari uzunlik va boshqaruv belgilariga qarshi tozalanadi. Navbat to‘lsa diagnostika tashlanadi va hisoblanadi; Grim tekshiruvlari davom etadi. Har bir flag diagnostika satriga aylantirilmaydi: player/check bo‘yicha cooldown qo‘llanadi.

Buffer hajmi va log rotation parametrlarini o‘zgartirish restart talab qiladi. Qolgan qoidalar muvaffaqiyatli reload’da bir yo‘la almashtiriladi. Amaldagi live o‘yin sinovlarisiz `0-lag` yoki nol false positive va’dasi berilmaydi.

`deployment/GrimAC/punishments.yml` — **ixtiyoriy enforcement boshlang‘ich profili**. Uni staging’da tekshirib, oldingi faylni zaxiralagach Grim papkasiga qo‘ying. U takroriy Simulation/Knockback/BadPackets/Reach/Hitboxes flaglarida kick qo‘shadi, avtomatik doimiy ban bermaydi. Thresholdlar universal kalibratsiya natijasi emas. Fayl avtomatik o‘rnatilmaydi; Grim’ning standart profilini qoldirish ham mumkin.

Sentinel tashqi telemetry/webhook yubormaydi. Grim o‘zining bStats va webhook sozlamalariga ega. Webhook’ni yoqishdan oldin upstream navbat/retry xatti-harakatini baholang; ushbu release’ning sinov konfiguratsiyasida Discord yuborish o‘chirilgan.

## Source’dan yig‘ish

JDK 25 va Maven 3.9+:

```sh
mvn -B clean verify
python3 tools/verify_artifact.py target/SentinelAC-2.4.0-paper26.2.jar
```

Natija: `target/SentinelAC-2.4.0-paper26.2.jar`. Paper API `26.2.build.135-stable`ga, Grim dependency esa Modrinth’dagi **Bukkit** version ID `baDrVvfo`ga pin qilingan. Oddiy `2.3.74` raqami Fabric buildiga ham mos kelishi sabab version ID ataylab ishlatiladi. Paper/Grim server dependency’lari JAR ichiga shade qilinmaydi.

Mahalliy startup/reload testlari uchun (vaqtinchalik offline localhost server):

```sh
python3 tools/smoke_paper.py --accept-eula --java /path/to/jdk-25/bin/java
npm ci --prefix tools/client
python3 tools/smoke_paper.py --accept-eula --mode via --client-probes --java /path/to/jdk-25/bin/java
```

`--accept-eula`ni faqat [Minecraft EULA](https://www.minecraft.net/eula)ni qabul qilgan bo‘lsangiz bering. Testlar production papkasiga tegmaydi; `target/smoke/`dan foydalanadi. Downloadlar `tools/test-dependencies.json`dagi pin va hashlar bilan tekshiriladi. Test klientlari server JAR’iga kiritilmaydi.

## Muallif va yadro

Sentinel modullari: **azicoo**. Fizika/paket yadrosi: **[GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim)** jamoasi. Litsenziya: [GPL-3.0](LICENSE). Protokol mosligi upstream Grim, PacketEvents, ViaVersion va ViaBackwards versiyalariga ham bog‘liq. Keyingi Paper versiyalari ushbu release qamroviga kirmaydi.
