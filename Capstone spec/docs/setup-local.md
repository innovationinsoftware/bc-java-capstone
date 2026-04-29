# Local Backend Setup Guide (Windows)

> Last updated: April 28, 2026  
> Environment: my.ablazedesktop.com VMs — Java Upskilling Class

---

## Prerequisites

- Oracle XE 21c installed
- Kafka 4.1.1 extracted to `C:\kafka`
- Java installed (for WireMock)
- PowerShell

---

## 1) Oracle XE

### Start Oracle services

```powershell
Start-Service OracleServiceXE
Start-Service OracleOraDB21Home1TNSListener
```

### Verify services are running

```powershell
Get-Service *Oracle* | Select-Object Name, Status
```

Expected running services:
- `OracleServiceXE`
- `OracleOraDB21Home1TNSListener`

### Verify port

```powershell
Test-NetConnection localhost -Port 1521
```

### Test DB login

```powershell
sqlplus bankapp/bankapp_password@localhost:1521/XEPDB1
```

---

## 2) Kafka 4.1.1 (KRaft Standalone)

### Verify installation

```powershell
cd C:\kafka
bin\windows\kafka-topics.bat --version
```

Expected output includes `4.1.1`.

### Configure log directory

Open `C:\kafka\config\server.properties` and set:

```properties
log.dirs=c:/kafka/data
```

> Use forward slashes even on Windows.

### Format storage (first time only)

**Generate UUID:**

```powershell
bin\windows\kafka-storage.bat random-uuid
```

Copy the UUID output.

**Create data directory:**

```powershell
mkdir C:\kafka\data
```

**Format storage:**

```powershell
bin\windows\kafka-storage.bat format -t <your-uuid> -c config\server.properties --standalone
```

Confirm output shows `c:/kafka/data`.

### Start Kafka

```powershell
cd C:\kafka
bin\windows\kafka-server-start.bat config\server.properties
```

> Keep this window open. Closing it stops the broker.

### Verify Kafka

In a new terminal:

```powershell
Test-NetConnection localhost -Port 9092
```

### Create test topic

```powershell
bin\windows\kafka-topics.bat --create --topic test-topic --bootstrap-server localhost:9092
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

> Do not delete the test topic — it can corrupt log files.

### Stop Kafka

```powershell
bin\windows\kafka-server-stop.bat
```

---

## 3) WireMock (Payment Processor Stub)

### Download (first time only)

```powershell
mkdir C:\wiremock
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/3.3.1/wiremock-standalone-3.3.1.jar" -OutFile "C:\wiremock\wiremock-standalone-3.3.1.jar"
```

### Start WireMock

```powershell
java -jar C:\wiremock\wiremock-standalone-3.3.1.jar --port 8089 --global-response-templating --disable-gzip --verbose
```

> Keep this window open.

### Verify WireMock

```powershell
Test-NetConnection localhost -Port 8089
```

---

## 4) Full Verification Checklist

Run all three port checks:

```powershell
Test-NetConnection localhost -Port 1521   # Oracle
Test-NetConnection localhost -Port 9092   # Kafka
Test-NetConnection localhost -Port 8089   # WireMock
```

All should return `TcpTestSucceeded : True`.

---

## Service Summary

| Service   | Port | Start Command                                                   |
|-----------|------|-----------------------------------------------------------------|
| Oracle XE | 1521 | `Start-Service OracleServiceXE` + `...TNSListener`              |
| Kafka     | 9092 | `bin\windows\kafka-server-start.bat config\server.properties`   |
| WireMock  | 8089 | `java -jar C:\wiremock\wiremock-standalone-3.3.1.jar ...`       |
