#include <dummy.h>

#include <Wire.h>
#include <ArduinoBLE.h>
#define CHARACTERISTIC_SIZE  20// Change based on your requirement
#define CHARACTERISTIC_SIZE_SECOND  20// Change based on your requirement
#include <DHT.h>
// #include <SparkFun_MAX1704x_Fuel_Gauge_Arduino_Library.h>

// #define DHTPIN 17     // red esp
#define DHTPIN 26     // Black esp
#define DHTTYPE DHT22   // DHT 22  (AM2302), AM2321
// #define LIGHT_SENSOR_PIN 25 //red esp
#define LIGHT_SENSOR_PIN 4 //black esp
#define RAIN_SENSOR_PIN 36
#define RAIN_SENSOR_2_PIN 39
#define RAIN_SENSOR_3_PIN 34
BLEService customService("3708a9dd-7544-44bb-b86b-400709028fc1");  // 1816 is the defined UUID for cycling tech...
BLECharacteristic txCharacteristic("4c64ae25-4411-49dd-baea-765bebcaf246",  // Custom characteristic UUID
                                   BLERead | BLENotify, 
                                   CHARACTERISTIC_SIZE);  // Characteristic value length
BLEDescriptor myDescriptor("07720b06-faba-43cc-b583-37c9d2fe02e9", "0");  // Used for enabling notifications.
DHT dht(DHTPIN, DHTTYPE);
// SFE_MAX1704X lipo(MAX1704X_MAX17048); // Allow access to all the 17048 features


void setup() {
  Serial.begin(115200);
  dht.begin();
  delay(1000);
  Serial.println("Starting...");

  Wire.begin();

  // // Set up the MAX17048 LiPo fuel gauge:
  // if (lipo.begin() == false) // Connect to the MAX17048 using the default wire port
  // {
  //   Serial.println(F("MAX17048 not detected. Please check wiring. Freezing."));
  //   while (1)
  //     ;
  // }

  // Initialize BLE hardware
  if (!BLE.begin()) {
    while (1) {
      Serial.println("Starting BLE failed!");
      delay(1000);
    }
  }
  
  // Set the local name and service information
  BLE.setLocalName("Wella PCB TEST");
  BLE.setAdvertisedService(customService);
  // Add custom characteristic
  customService.addCharacteristic(txCharacteristic);
  txCharacteristic.addDescriptor(myDescriptor);
  BLE.addService(customService);
  
  // Start advertising
  BLE.advertise();
  Serial.println("Bluetooth device active, waiting for connections...");
}



void loop() {
  BLEDevice central = BLE.central();
  Serial.println("Waiting to connect to central.");
  if (central) {
    Serial.print("Connected to central: ");
    Serial.println(central.address());
    while (central.connected()) {
      String data_string_3 = "000 ";
      float battery_percent = 72.2; //lipo.getSOC();
      data_string_3 = data_string_3 + String(battery_percent, 2);
      uint8_t packet_3[CHARACTERISTIC_SIZE];     // initialize packet data array
      data_string_3.getBytes(packet_3, CHARACTERISTIC_SIZE);
      txCharacteristic.writeValue(packet_3, CHARACTERISTIC_SIZE);
      delay(10);

      float f = dht.readTemperature(true);
      float h = dht.readHumidity();
      // int sensorValue = analogRead(LIGHT_SENSOR_PIN);  // Read the analog value from sensor
      // Check if any reads failed and exit early (to try again).
      if (isnan(h) || isnan(f)) {
        Serial.println(F("Failed to read from DHT sensor!"));
        return;
      }

      String data_string = "999 ";
      String temp_string = String(f, 2);
      String hum_string = String(h, 2);
      data_string = data_string + temp_string + " " + hum_string;
      /// + " " + light_string
      uint8_t packet[CHARACTERISTIC_SIZE];     // initialize packet data array
      data_string.getBytes(packet, CHARACTERISTIC_SIZE);
      txCharacteristic.writeValue(packet, CHARACTERISTIC_SIZE);
      delay(10);


      String data_string_2 = "111 ";
      int analogValue = analogRead(LIGHT_SENSOR_PIN);
      String light_string;
      Serial.print("Light: ");
      Serial.println(analogValue);
      if (analogValue < 500) {
        light_string = "1";
      } else if (analogValue < 2000) {
        light_string = "2";
      } else if (analogValue < 3000) {
        light_string = "3";
      } else if (analogValue < 4000) {
        light_string = "4";
      } else {
        light_string = "5";
      }

      int rainSensorValue = analogRead(RAIN_SENSOR_PIN);  // Read the analog value from sensor
      //int rainSensorValue2 = analogRead(RAIN_SENSOR_2_PIN);  // Read the analog value from sensor
      Serial.print("Rain: ");
      Serial.println(rainSensorValue);
      //Serial.print("Rain2: ");
      //Serial.println(rainSensorValue2);
      String rain_string;
      if (rainSensorValue < 4000) { // || rainSensorValue2 < 4000) {
        rain_string = "1";
      } else {
        rain_string = "2";
      } 
      data_string_2 = data_string_2 + light_string + " " + rain_string;
      uint8_t packet_2[CHARACTERISTIC_SIZE];     // initialize packet data array

      data_string_2.getBytes(packet_2, CHARACTERISTIC_SIZE);
      txCharacteristic.writeValue(packet_2, CHARACTERISTIC_SIZE);
      delay(2000);  // check sensor data every 2s
    }
      
    Serial.print("Disconnected from central: ");
    Serial.println(central.address());
    delay(1000);
  }
}