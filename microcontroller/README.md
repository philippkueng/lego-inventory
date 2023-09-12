In order to control the servos, stepper motors etc. I decided to go with an ESP32 controller running [esprit](https://github.com/mfikes/esprit) so I have this REPL driven development flow while building the machine.

I purchased a [Lolin D32 Pro](https://www.bastelgarage.ch/lolin-d32-pro-esp32-board-16mb-flash-8mb-psram) with enough flash storage and psram for esprit to run on and then followed the [getting started guide](https://cljdoc.org/d/esprit/esprit/1.0.0/doc/getting-started) to flash the base runtime onto the microcontroller and connect to it.

```bash
clj -M -m cljs.main -co '{:closure-defines {esprit.repl/wifi-ssid "the-wifi-network" esprit.repl/wifi-password "the-wifi-password"} :optimizations :simple :target :none :browser-repl false :process-shim false}' -c esprit.repl
clj -M -m esprit.make-rom
clj -M -m esprit.flash --erase
clj -M -m esprit.flash --bootstrap
clj -M -m esprit.flash --flash out/main.bin
```

Create a REPL connection to the controller and toggle the blue LED on the board.

```
➜  microcontroller git:(master) ✗ clj -M -m cljs.main -re esprit -ro '{:endpoint-address "192.168.0.249"}' -r

Connecting to ESP32 WROVER ...

ClojureScript 1.10.764
cljs.user=> (def led js/D5)
#'cljs.user/led
cljs.user=> (.write led false)
nil
cljs.user=> (.write led true)
nil
```
