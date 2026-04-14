#include <WiFi.h>
#include <WiFiUdp.h>
#include <secrets.h>
#include "driver/i2s.h"

const char* ssid = WIFI_SSID;
const char* password = WIFI_PASS;

const char* host = PHONE_IP; 
const int port = 50005;

WiFiUDP udp;

#define I2S_WS 11
#define I2S_SCK 12
#define I2S_SD 13
#define LED_PIN 48
#define MOTOR_PIN 15

void setup_i2s() {
    i2s_config_t i2s_config = {
        .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
        .sample_rate = 16000,
        .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
        .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
        .communication_format = I2S_COMM_FORMAT_STAND_I2S,
        .dma_buf_count = 8,
        .dma_buf_len = 512,
        .use_apll = false
    };
    i2s_pin_config_t pin_config = {
        .bck_io_num = I2S_SCK,
        .ws_io_num = I2S_WS,
        .data_out_num = -1,
        .data_in_num = I2S_SD
    };
    i2s_driver_install(I2S_NUM_0, &i2s_config, 0, NULL);
    i2s_set_pin(I2S_NUM_0, &pin_config);
}

void setup() {
    Serial.begin(115200);
    pinMode(MOTOR_PIN, OUTPUT);
    digitalWrite(MOTOR_PIN, LOW); 
    WiFi.begin(ssid, password);
    udp.begin(port); 
    setup_i2s();
}

void loop() {
    while (WiFi.status() != WL_CONNECTED) { 
        delay(500); 
        Serial.print("."); 
    }
    Serial.println("\nWi-Fi Connected! IP: " + WiFi.localIP().toString());
    static uint8_t buffer[1024];
    size_t bytes_read;

    i2s_read(I2S_NUM_0, buffer, sizeof(buffer), &bytes_read, portMAX_DELAY);

    if (WiFi.status() == WL_CONNECTED && bytes_read > 0) {
        if (udp.beginPacket(host, port)) {
            udp.write(buffer, bytes_read);
            if (!udp.endPacket()) {
                Serial.println("Ошибка отправки UDP!");
            }
        } else {
            Serial.println("Не могу начать пакет (проверь IP!)");
        }
    }
 int packetSize = udp.parsePacket();
    if (packetSize) {
        char cmd = udp.read();
        Serial.printf("Получена команда: %c\n", cmd);
        if (cmd == 'V') {
            Serial.println("ВИБРАЦИЯ");
            digitalWrite(MOTOR_PIN, HIGH); 
            neopixelWrite(LED_PIN, 255, 0, 0);
            delay(1000); 
            digitalWrite(MOTOR_PIN, LOW); 
            neopixelWrite(LED_PIN, 0, 0, 0);
        }
    }
}