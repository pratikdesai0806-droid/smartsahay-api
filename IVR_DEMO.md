# SmartSahay IVR demo (branch `feature/ivr-demo`)

A simulated voice helpline: an on-screen phone keypad that reads prompts aloud in the browser and calls two new API endpoints.
It is a demo only. It is not a real phone number and needs no telephony provider.

## What was added

| Type | File |
|---|---|
| New | `src/main/java/com/smartsahay/servlet/IvrServlet.java` (`/api/ivr/*`) |
| New | `src/main/java/com/smartsahay/service/IvrService.java` (keypad codes to request, text for speech) |
| Edited | `model/EligibilityRequest.java`: added static `of(...)` |
| Edited | `model/EligibilityResult.java`: added `getEligibleCount()` and `getEligible()` |
| New | `smartsahay-frontend/src/components/IvrKeypad.jsx` and `IvrKeypad.css` |
| New | `smartsahay-frontend/src/pages/IvrDemo.jsx` |
| Edited | `smartsahay-frontend/src/App.jsx` (route `/ivr`) and `components/Navbar.jsx` ("Voice Demo" link) |

The edits to existing files only add code, and the existing JSON responses do not change.

## API

```
GET  /api/ivr/scheme?digits=1          menu keys 1..6
POST /api/ivr/eligibility              {"state":1,"age":35,"category":2,"acres":3,"income":2,"crop":3}
```
Both return `{"gather_prompt":{"text":"..."},"max_input_digits":1}`.

Keypad codes: state 1 = Karnataka, 2 = other. Category 1..5 = General, SC, ST, OBC, Minority. Crop 1..4 = Kharif, Rabi, Millets, Other.
Income 1..5 = below 1 lakh, 1 to 3, 3 to 6, 6 to 8, above 8 lakh (the upper bound is used for matching). Acres are whole acres (0 = no land).
Eligibility reuses `EligibilityService` unchanged, so results should match the `/eligibility` page for the same inputs.
Answers are sent in a POST body and are never logged or stored.

## How to check it

1. Run the database, `db.properties` and Tomcat 10.1 as in `README.md`.
2. `http://localhost:8080/smartsahay/api/health` should report OK.
3. In Command Prompt:
   ```
   curl "http://localhost:8080/smartsahay/api/ivr/scheme?digits=1"
   curl "http://localhost:8080/smartsahay/api/ivr/scheme?digits=9"
   curl -X POST http://localhost:8080/smartsahay/api/ivr/eligibility -H "Content-Type: application/json" -d "{\"state\":1,\"age\":35,\"category\":2,\"acres\":3,\"income\":2,\"crop\":3}"
   ```
   The first returns the spoken PM-KISAN summary. The second returns a "not on the menu" message. The third returns a sentence listing up to three schemes.
4. `cd smartsahay-frontend`, then `npm install`, then `npm run dev`, then open `http://localhost:5173/ivr`.
   Try scheme keys 1 to 6, then the eligibility mode, then compare the same profile with the `/eligibility` page.
5. Check the Tomcat log: it must not show any farmer answers.

## Status: please read

- Tested only outside the real app: the IVR logic compiled and ran against these model classes, and the keypad page ran in a simulated browser.
- **Not yet compiled in this repo or run against MySQL and Tomcat.** `IvrServlet.java` needs the Jakarta Servlet API, so it has never been built. Run `mvn clean package` first and report any compile error.
- Browser speech depends on the device and browser (Chrome or Edge recommended). A transcript is also shown under the keypad.
- This backend ignores the "FRUITS ID" and "Aadhaar-linked bank" questions because its rules do not use them, so the keypad asks only 6 questions.
- Results are indicative only, not an official eligibility decision.
