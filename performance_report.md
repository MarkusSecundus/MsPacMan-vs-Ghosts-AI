Vše bylo měřeno na notebooku HP Spectre x360 14" s CPU Intel Core Ultra 7 155H, Windows 11, s připojeným napájením a power profilem nastaveným na nejlepší výkon.

První dvě hodnoty naměřit bylo poměrně triviální - víceméně jsem copypastnul logiku pro vytvoření instance hry z MsPacMan.java a nad získanou instancí hry pak hodněkrát pustil kód, který jsem chtěl změřit.   
advanceGame() bylo nepatrně zákeřné v tom, že po nějaké době vyústí v konec hry a volat ho poté už nedává moc smysl - to jsem vyřešil tak, že jsem si prostě předpřipravil obrovské pole klonů počátečního herního stavu do zásoby, a jakmile jeden dohrál, skočil jsem na další. To možná nezní úplně dokonale košer, protože taková akce je potenciálně drahý cache-miss (další klon herního stavu mohl být v paměti naalokovaný daleko od toho předchozího), nicméně co jsem počítal, na jednu tuhle akci vycházelo zhruba 380 volání advanceGame(), takže snad by se to v tom mělo ztratit.   

Obě akce jsem nejprve běžel 500000krát na warmup a následně stejněkrát samotné měření, toto celé opakováno 10krát.
Výsledky:
```
1...
forward model clone: 2319 ns
forward model advance: 191 ns
2...
forward model clone: 2572 ns
forward model advance: 218 ns
3...
forward model clone: 2351 ns
forward model advance: 142 ns
4...
forward model clone: 3323 ns
forward model advance: 135 ns
5...
forward model clone: 2305 ns
forward model advance: 113 ns
6...
forward model clone: 2414 ns
forward model advance: 126 ns
7...
forward model clone: 2319 ns
forward model advance: 131 ns
8...
forward model clone: 2440 ns
forward model advance: 123 ns
9...
forward model clone: 2223 ns
forward model advance: 135 ns
10...
forward model clone: 2244 ns
forward model advance: 146 ns
```

Klonování modelu se drželo cca kolem 2.4 us, advance, jak vidno, trvá pouhých cca 6% toho co klonování.


-------------------------


Měření iterace A* jsem se rozhodl provést v co nejautentičtějších podmínkách - přímo do algoritmu jsem injectnul profilovací kód a výsledky vypisoval skrz debugovací zprávy na konci každého levelu.

Výsledky:
```
Level 1... 4150 ns per aStar iteration, 18061692 ns per run (6527641 iterations, 1500 runs, 27092538200 ns total)
Level 2... 4323 ns per aStar iteration, 5393662 ns per run (3692366 iterations, 2960 runs, 15965240600 ns total)
Level 3... 4504 ns per aStar iteration, 2679107 ns per run (2565577 iterations, 4314 runs, 11557671000 ns total)
Level 4... 4634 ns per aStar iteration, 1363550 ns per run (1695559 iterations, 5763 runs, 7858144000 ns total)
Level 5... 4487 ns per aStar iteration, 2052093 ns per run (3346297 iterations, 7318 runs, 15017222200 ns total)
Level 6... 4505 ns per aStar iteration, 1271458 ns per run (2469793 iterations, 8752 runs, 11127808900 ns total)
Level 7... 4369 ns per aStar iteration, 2077603 ns per run (4894096 iterations, 10292 runs, 21382699700 ns total)
Level 8... 4412 ns per aStar iteration, 1553453 ns per run (4168819 iterations, 11840 runs, 18392893800 ns total)
Level 9... 4204 ns per aStar iteration, 1814297 ns per run (5767718 iterations, 13367 runs, 24251710400 ns total)
Level 10... 4559 ns per aStar iteration, 935242 ns per run (3072356 iterations, 14978 runs, 14008058300 ns total)
Level 11... 4186 ns per aStar iteration, 1452314 ns per run (5823511 iterations, 16787 runs, 24379999100 ns total)
Level 12... 4585 ns per aStar iteration, 438547 ns per run (1752381 iterations, 18324 runs, 8035941900 ns total)
Level 13... 4560 ns per aStar iteration, 594781 ns per run (2599974 iterations, 19934 runs, 11856383800 ns total)
Level 14... 4457 ns per aStar iteration, 871338 ns per run (4193999 iterations, 21453 runs, 18692826000 ns total)
Level 15... 4421 ns per aStar iteration, 862322 ns per run (4502330 iterations, 23087 runs, 19908430800 ns total)
```

Celé měření běželo mnoho minut, běhů algoritmu byly provedeny vyšší desítky tisíc, jednotlivých iterací pak nižší desítky milionů - budeme-li tedy primárně přikládat váhu datům z pozdějších levelů, dá se asi s klidným svědomím předpokládat, že řádný warmup proběhl atd. .    
Je uklidňující vidět, že kompletní jeden běh algoritmu v průměru trvá jenom cca 1 až 5 ms, takže do limitu 40 ms se vejde s krásnou rezervou.   

Také je vidět, že jedna iterace algoritmu odpovídá cca necelým dvěma klonováním forward modelu.   

Tohle pozorování ve mně vyvolalo myšlenku na optimalizaci - pokud očividně v průměru provede a* v jednom uzlu takhle málo (typicky < 2) větvení, proč dělám pokaždé klon herního stavu, když by někdy stačilo znovu využít ten, co už mám z parenta? Obecně, můžu vlastně forward model z parenta zrecyklovat vždycky - pro posledního syna, kterého otevírám.


Po troše debugování jsem tuhle myšlenku úspěšně implementoval - chování pacmana se zdá nezměněné a výsledky velmi silně předčily očekávání:
```
Level 1... 954 ns per aStar iteration, 1464081 ns per run (1881471 iterations, 1227 runs, 1796427900 ns total)
Level 2... 743 ns per aStar iteration, 1628767 ns per run (5917253 iterations, 2702 runs, 4400929300 ns total)
Level 3... 972 ns per aStar iteration, 504975 ns per run (2139341 iterations, 4119 runs, 2079994300 ns total)
Level 4... 875 ns per aStar iteration, 306073 ns per run (1919833 iterations, 5493 runs, 1681263300 ns total)
Level 5... 901 ns per aStar iteration, 377540 ns per run (2959653 iterations, 7068 runs, 2668455100 ns total)
Level 6... 868 ns per aStar iteration, 452553 ns per run (4445235 iterations, 8526 runs, 3858474000 ns total)
Level 7... 906 ns per aStar iteration, 418800 ns per run (4672100 iterations, 10109 runs, 4233654000 ns total)
Level 8... 949 ns per aStar iteration, 215076 ns per run (2612191 iterations, 11535 runs, 2480904700 ns total)
Level 9... 4528 ns per aStar iteration, 4709697 ns per run (15116716 iterations, 14535 runs, 68455450000 ns total)
Level 10... 878 ns per aStar iteration, 310185 ns per run (5754107 iterations, 16298 runs, 5055404000 ns total)
Level 11... 907 ns per aStar iteration, 199868 ns per run (3902098 iterations, 17716 runs, 3540868700 ns total)
Level 12... 873 ns per aStar iteration, 188502 ns per run (4159769 iterations, 19283 runs, 3634888200 ns total)
Level 13... 817 ns per aStar iteration, 326479 ns per run (8428494 iterations, 21113 runs, 6892952800 ns total)
Level 14... 847 ns per aStar iteration, 539032 ns per run (14527735 iterations, 22848 runs, 12315818100 ns total)
Level 15... 952 ns per aStar iteration, 188217 ns per run (4833228 iterations, 24458 runs, 4603426200 ns total)
```

Nějakým způsobem (až na 9. level) čas klesl ještě víc než o těch 2400 ns, které by mělo trvat to jedno ušetřené volání copy(). Netuším jak je to možné, nemám energii nad tím bádat tbh, ale je to fajn.









Vypracoval Jakub Hroník