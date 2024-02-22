// code for the webserver & wifi part from
// https://techoverflow.net/2021/01/29/esp32-minimal-json-webserver-example-for-platformio-espasyncwebserver/

#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>

AsyncWebServer server(80);

// put function declarations here:
int myFunction(int, int);

void setup() {
  Serial.begin(115200);
  // Connect Wifi, restart if not connecting
  // https://techoverflow.net/2021/01/21/how-to-fix-esp32-not-connecting-to-the-wifi-network/
  WiFi.begin("network-name", "network-password");
  uint32_t notConnectedCounter = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
    Serial.println("Wifi connecting...");
    notConnectedCounter++;
    if(notConnectedCounter > 150) { // Reset board if not connected after 15s
      Serial.println("Resetting due to Wifi not connecting...");
      ESP.restart();
    }
  }
  Serial.print("Wifi connected, IP address: ");
  Serial.println(WiFi.localIP());
  // Initialize webserver URLs
  server.on("/api/wifi-info", HTTP_GET, [](AsyncWebServerRequest *request) {
      AsyncResponseStream *response = request->beginResponseStream("application/json");
      DynamicJsonDocument json(1024);
      json["status"] = "ok";
      json["ssid"] = WiFi.SSID();
      json["ip"] = WiFi.localIP().toString();
      serializeJson(json, *response);
      request->send(response);
  });

  // Start webserver
  server.begin();
}

void loop() {
  // put your main code here, to run repeatedly:
}

// put function definitions here:
int myFunction(int x, int y) {
  return x + y;
}