#include <Wire.h>
#include <ArduinoBLE.h>
#define CHARACTERISTIC_SIZE  7// Change based on your requirement
BLEService customService("3708a9dd-7544-44bb-b86b-400709028fc1");  // 1816 is the defined UUID for cycling tech...
BLECharacteristic txCharacteristic("4c64ae25-4411-49dd-baea-765bebcaf246",  // Custom characteristic UUID
                                   BLERead | BLENotify, 
                                   CHARACTERISTIC_SIZE);  // Characteristic value length
BLEDescriptor myDescriptor("07720b06-faba-43cc-b583-37c9d2fe02e9", "0");  // Used for enabling notifications.



void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("Starting...");

  // Initialize BLE hardware
  if (!BLE.begin()) {
    while (1) {
      Serial.println("Starting BLE failed!");
      delay(1000);
    }
  }
  
  // Set the local name and service information
  BLE.setLocalName("Group 5 ESP Bluetooth device");
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
      uint8_t packet[CHARACTERISTIC_SIZE];     // initialize packet data array
      String data_string = "my ble ";
      data_string.getBytes(packet, CHARACTERISTIC_SIZE);
      txCharacteristic.writeValue(packet, CHARACTERISTIC_SIZE);
      delay(200);  // check sensor data every 100ms

      data_string = "is now ";
      data_string.getBytes(packet, CHARACTERISTIC_SIZE);
      txCharacteristic.writeValue(packet, CHARACTERISTIC_SIZE);
      delay(200);  // check sensor data every 100ms

      data_string = "working";
      data_string.getBytes(packet, CHARACTERISTIC_SIZE);
      txCharacteristic.writeValue(packet, CHARACTERISTIC_SIZE);
      delay(200);  // check sensor data every 100ms
    }
      
    Serial.print("Disconnected from central: ");
    Serial.println(central.address());
    delay(1000);
  }
}