// code for the webserver & wifi part from
// https://techoverflow.net/2021/01/29/esp32-minimal-json-webserver-example-for-platformio-espasyncwebserver/

#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <ElegantOTA.h>

AsyncWebServer server(80);

// put function declarations here:
int myFunction(int, int);

constexpr int ONBOARD_LED = 5;

// copied from https://www.makerguides.com/esp32-and-tb6600-stepper-motor-driver
constexpr int PUL = 25; //define Pulse pin
constexpr int DIR = 26; //define Direction pin
constexpr int ENA = 27; //define Enable Pin
bool stepperMotorOn = false;
long stepperMotorPreviousMicroseconds = 0;        // will store last time the stepper motor was updated
long stepperMotorInterval = 50; // microseconds

constexpr int SERVO_PIN = 13; // ESP32 pin GPIO13 connected to servo motor - the yellow cable
bool servoOn = false;
long servoPreviousMicroseconds = 0;
long servoInterval = 15 * 1000; // 15ms
int servoPosition = 0;
enum ServoDirection { up, down };
ServoDirection servoDirection = up;
Servo servoMotor;

void setup() {
  Serial.begin(115200);

  // initializing the LED
  pinMode(ONBOARD_LED, OUTPUT);
  digitalWrite(ONBOARD_LED, HIGH);

  // initialize the stepper motor
  pinMode(PUL, OUTPUT);
  pinMode(DIR, OUTPUT);
  pinMode(ENA, OUTPUT);
  digitalWrite(PUL, LOW);
  digitalWrite(DIR, HIGH);
  digitalWrite(ENA, LOW);

  servoMotor.attach(SERVO_PIN);  // attaches the servo on ESP32 pin

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

  server.on("/api/start", HTTP_GET, [](AsyncWebServerRequest *request) {
    AsyncResponseStream *response = request->beginResponseStream("application/json");
    DynamicJsonDocument json(1024);
    json["status"] = "ok";
    json["message"] = "started";
    digitalWrite(ONBOARD_LED, LOW);
    Serial.println("turned LED on");
    stepperMotorOn = true;
    servoOn = true;
    serializeJson(json, *response);
    request->send(response);
  });

  server.on("/api/stop", HTTP_GET, [](AsyncWebServerRequest *request) {
    AsyncResponseStream *response = request->beginResponseStream("application/json");
    DynamicJsonDocument json(1024);
    json["status"] = "ok";
    json["message"] = "stopped";
    digitalWrite(ONBOARD_LED, HIGH);
    Serial.println("turned LED off");
    stepperMotorOn = false;
    servoOn = false;
    serializeJson(json, *response);
    request->send(response);
  });

  ElegantOTA.begin(&server);

  // Start webserver
  server.begin();
}

void loop() {
  unsigned long currentMicroseconds = micros();

  // stepper motor
  if (stepperMotorOn == true && (currentMicroseconds - stepperMotorPreviousMicroseconds) > stepperMotorInterval) {
    // save the last time we triggered
    stepperMotorPreviousMicroseconds = currentMicroseconds;

    // trigger the stepper motor
    digitalWrite(PUL, !digitalRead(PUL));
  }

  // servo
  if (servoOn == true && (currentMicroseconds - servoPreviousMicroseconds) > servoInterval) {
    // save the last time we triggered
    servoPreviousMicroseconds = currentMicroseconds;

    // trigger the servo
    if (servoDirection == up) {
      // moving up
      if (servoPosition <= 180) {
        // continue sweeping
        servoPosition = servoPosition + 1;
        servoMotor.write(servoPosition);
      } else {
        // we've reached the end and need to switch direction
        servoDirection = down;
      }
    } else {
      // moving down
      if (servoPosition >= 0) {
        servoPosition = servoPosition - 1;
        servoMotor.write(servoPosition);
      } else {
        servoDirection = up;
      }
    }
  }
  // // rotates from 0 degrees to 180 degrees
  // for (int pos = 0; pos <= 180; pos += 1) {
  //   // in steps of 1 degree
  //   servoMotor.write(pos);
  //   delay(15); // waits 15ms to reach the position
  // }
  //
  // // rotates from 180 degrees to 0 degrees
  // for (int pos = 180; pos >= 0; pos -= 1) {
  //   servoMotor.write(pos);
  //   delay(15); // waits 15ms to reach the position
  // }

  ElegantOTA.loop();
}

// put function definitions here:
int myFunction(int x, int y) {
  return x + y;
}