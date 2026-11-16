#include <SoftwareSerial.h>
#include <Servo.h>

// ---------- Pines (armado en protoboard) ----------
#define Sensor A0       // MQ-2 (cable verde en A0)
#define Buzzer 9        // Buzzer activo (B15 -> D9)
#define LED 8           // LED en D8
#define PinServo 6      // Servo de la válvula (señal en D6)

// HC-05: D10 recibe del TXD del HC-05, D11 envía al RXD del HC-05
SoftwareSerial bluetooth(10, 11);
Servo valvula;

// ---------- Configuración ----------
const int umbralGas = 350;          // sobre esto hay ALERTA
const int histeresis = 30;          // baja de umbral-30 para volver a NORMAL (evita parpadeo)
const unsigned long calentamiento = 20000; // 20 s de calentamiento del MQ-2
const int anguloAbierta = 90;
const int anguloCerrada = 0;

// ---------- Estado ----------
bool alerta = false;
bool valvulaAbierta = true;
bool buzzerOn = false;
unsigned long tTrama = 0, tBuzzer = 0;
String comando = "";

void moverValvula(bool abrir) {
  valvulaAbierta = abrir;
  valvula.write(abrir ? anguloAbierta : anguloCerrada);
}

// Comandos de la app: "VAL:ABRIR", "VAL:CERRAR" (FAN:n se ignora, no hay ventilador)
void procesar(String cmd) {
  cmd.trim();
  if (cmd == "VAL:CERRAR") {
    moverValvula(false);
  } else if (cmd == "VAL:ABRIR") {
    if (!alerta) moverValvula(true);   // fail-safe: nunca se abre el gas con fuga
  }
}

void leerComandos(Stream &s) {
  while (s.available()) {
    char c = s.read();
    if (c == '\n') { procesar(comando); comando = ""; }
    else if (c != '\r' && comando.length() < 20) comando += c;
  }
}

void setup() {
  Serial.begin(9600);
  bluetooth.begin(9600);

  pinMode(Buzzer, OUTPUT);
  pinMode(LED, OUTPUT);
  digitalWrite(Buzzer, LOW);
  digitalWrite(LED, LOW);

  valvula.attach(PinServo);
  moverValvula(true);
}

void loop() {
  unsigned long ahora = millis();

  // Comandos desde el teléfono (Bluetooth) o desde el Monitor Serie (pruebas por USB)
  leerComandos(bluetooth);
  leerComandos(Serial);

  int nivelGas = analogRead(Sensor);
  bool calentando = ahora < calentamiento;

  // ---------- Lógica de alarma (con histéresis) ----------
  if (!calentando) {
    if (!alerta && nivelGas >= umbralGas) {
      alerta = true;
      moverValvula(false);   // fail-safe: corta el gas
    } else if (alerta && nivelGas < umbralGas - histeresis) {
      alerta = false;        // la válvula queda cerrada hasta que el usuario la abra
    }
  }

  // LED y buzzer intermitente sin delay() (para no perder comandos)
  digitalWrite(LED, alerta ? HIGH : LOW);
  if (alerta) {
    if (ahora - tBuzzer >= 300) {
      tBuzzer = ahora;
      buzzerOn = !buzzerOn;
      digitalWrite(Buzzer, buzzerOn ? HIGH : LOW);
    }
  } else {
    buzzerOn = false;
    digitalWrite(Buzzer, LOW);
  }

  // ---------- Trama para la app cada 500 ms ----------
  // GAS:412;EST:ALERTA;VAL:CERRADA;FAN:0
  if (ahora - tTrama >= 500) {
    tTrama = ahora;
    String trama = "GAS:" + String(nivelGas) +
                   ";EST:" + (calentando ? "CALENTANDO" : (alerta ? "ALERTA" : "NORMAL")) +
                   ";VAL:" + (valvulaAbierta ? "ABIERTA" : "CERRADA") +
                   ";FAN:0";
    bluetooth.println(trama);
    Serial.println(trama);
  }
}
