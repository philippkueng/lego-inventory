Open this directory with VS Code and the PlatformIO plugin. Then build and upload the code once. (for this one has to disable the `extra_scripts`, `upload_protocol` and `custom_upload_url`)

After this set the `custom_upload_url` to the hostname that the controller is assigning itself.

Now ensure in the Privacy & Security Settings dialog on macOS that VS Code has permissions to access the Local Network. Without this the python script will fail as it runs within the security context of VS Code.

Another option is to run it from the Terminal with `pio run -t uploadfsota` (this then uses the security context of eg. iTerm)

I'll be using this Board for it: (most likely the "ESP32 Modul DevKit V1 one from mikroshop.ch") with this pinout https://www.espboards.dev/esp32/esp32doit-devkit-v1/
