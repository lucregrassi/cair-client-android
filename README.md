# CAIR Client per Robot Pepper 🤖

**CAIR Client** è un'applicazione Android sviluppata in **Kotlin** per il robot umanoide **Pepper**, progettata per supportare interazioni conversazionali intelligenti e interventi programmati. L'applicazione è pensata per scenari reali come ospedali, stazioni marittime, musei, e contesti assistenziali.

---

## 🚀 Funzionalità principali

- 🎤 Riconoscimento vocale con microfono esterno o integrato
- 🗣️ Conversazioni personalizzate tramite LLM e API Azure/OpenAI
- 🕑 Interventi programmati (immediati, a orario fisso, periodici)
- 🧠 Personalizzazione degli argomenti e delle domande da porre
- 🔐 Salvataggio sicuro delle impostazioni con `EncryptedSharedPreferences`
- 🎛️ Interfaccia grafica per configurazioni rapide
- 📱 Adattamento dinamico di **dimensione del testo** e **velocità della voce**
- ⚙️ Teleoperazione del robot via UDP
- 🧪 Logging dettagliato per test su campo

---

## 🧩 Architettura

- **`MainActivity`**: gestisce l’interazione principale con il robot.
- **`SettingsActivity`**: consente la configurazione di parametri come IP, API keys, identità e preferenze utente.
- **`InterventionFragment`**: per la programmazione di interventi (interviste, topic, azioni).
- **`PersonalizationFragment`**: modifica dinamica dei parametri di dialogo.
- **`PepperInterface`**: incapsula le API QiSDK per controllare il robot.
- **`AudioRecorder`**: gestisce efficientemente l'acquisizione dell'input dell'utente dal microfono del tablet.
- **`ServerCommunicationManager`**: connessione al server CAIR per ottenere le risposte.

---

## ⚙️ Requisiti

- 🤖 Robot **Pepper** con **QiSDK** installato
- 💻 **Android Studio Meerkat** o superiore
- 📱 **Android 6.0** (sul tablet di Pepper)
- 🌐 Connessione internet per usare le API Azure e connettersi al server CAIR
- 🔑 Chiavi API valide per OpenAI e Azure Speech

---

## 🛠️ Configurazione

1. Clona il repository:
   ```bash
   git clone https://github.com/lucregrassi/cair-client-android.git
   ```
2.	Apri il progetto in Android Studio.
3.	Inserisci il certificato .crt per connettersi al server in modo sicuro nella cartella assets/certificates.
4.  Connettiti al tablet di Pepper
    L’IP del tablet è visibile abbassando la tendina del centro di controllo del tablet (quello con icona arancione con scritto a fianco "For Run/Debug Config").  
    Apri il terminale e digita:
    ```bash
    adb connect <ip-del-robot>
    ```
6.	Attendi che su Android Studio in alto compaia il nome ARTNCORE LPT_200AR e premi il simbolo play per installare l'app sul tablet di Pepper.

---

## ▶️ Come usare l'app
1.	Avvia l’app sul tablet di Pepper.
2.	Imposta i parametri iniziali nella sezione Impostazioni.
3.	Premi il tasto "Avanti" per iniziare il dialogo.
4. Accedi alla sezione **"Interventi"** tramite il **Menu** (i tre puntini in alto a destra) per:
   - ✅ Aggiungere **domande predefinite**
   - 🧠 Programmare **argomenti di conversazione**
   - 🤖 Impostare **azioni robotiche**

5. Accedi alla sezione **"Personalizzazione"** per modificare le *dialogue nuances*, ovvero i valori che vengono usati per **personalizzare il dialogo** in tempo reale.

6. I dati vengono **salvati in modo sicuro** e **ricaricati automaticamente al riavvio** dell’app.

---

## 🔐 Salvataggio sicuro
-	Le preferenze utente vengono salvate in EncryptedSharedPreferences (AES-256).
-	I file relativi al dialogo sono salvati su memoria interna (filesDir) tramite FileStorageManager.

⸻

## 🧪 Log & Debug
-	I tempi di risposta e i tempi di riproduzione vocale vengono mandati automaticamente al server per poter essere analizzati.

⸻

## 📄 Licenza

Distribuito sotto licenza MIT.
