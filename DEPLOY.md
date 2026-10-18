# Deploying the Math Tests Studio website

*Courtesy of Miss Sarah Awad – Math Teacher*

This guide sets up the website from nothing: the student site, the iPad Studio page and the Google Sheet behind them. It also covers updates and checks. It takes about 30 minutes the first time.

The website has three parts:

| Part | Where it runs | What it does |
|---|---|---|
| Website (`docs/`) | GitHub Pages | Sign-in, registration, home screen, the tests in `GradeN/`, and the Studio page `studio.html` |
| Backend (`backend/Code.gs`) | Google Apps Script, bound to the Sheet | Signs users in, grades tests, saves results, and saves Studio pages to GitHub |
| Database | A private Google Sheet | Tabs `Users`, `Tests`, `Questions` (the answer key) and `Results` |

The answer key lives only in the Sheet. Published pages never contain the answers.

---

## What you need

- A GitHub account.
- A Google account (for the Sheet and Apps Script).
- This repo, `math-tests-studio`, on GitHub. Fork it or upload a copy. Keep it **public**, because free GitHub Pages needs a public repo.

---

## Step 1 – Turn on GitHub Pages

1. On github.com, open the repo and go to **Settings → Pages**.
2. Under **Build and deployment**, set:
   - **Source:** *Deploy from a branch*
   - **Branch:** `main`
   - **Folder:** `/docs`
3. Click **Save**.
4. After 1–2 minutes the page shows the site address:
   `https://<your-user>.github.io/math-tests-studio/`

The site will open now, but sign-in fails until steps 2 and 3 are done.

---

## Step 2 – Create the Google Sheet and backend

1. In Google Drive, create a **new blank Google Sheet**. Name it, for example *Math Tests Studio – Database*.
2. In the Sheet, open **Extensions → Apps Script**.
3. Delete the sample code. Paste the whole of `backend/Code.gs` and click **Save** (the disk icon).
4. In the function list at the top, choose **`setup`** and click **Run**.
   - Google asks for permission. Click **Review permissions**, choose your account, then **Advanced → Go to … (unsafe) → Allow**. It says "unsafe" because you wrote the script yourself and Google has not checked it.
   - `setup()` creates the four tabs and a secret for signing sessions. You can run it again safely.
5. Click **Deploy → New deployment**.
   - Click the gear next to **Select type** and choose **Web app**.
   - **Execute as:** *Me*
   - **Who has access:** *Anyone*
   - Click **Deploy**, then **Allow** if asked again.
6. Copy the **Web app URL**. It ends in `/exec`.

> Make only **one** deployment. For every later code change use **Manage deployments → Edit** (step 7). That keeps the same URL.

---

## Step 3 – Connect the website to the backend

Put the `/exec` URL into `docs/config.js`.

**With the desktop app:** open **Settings**, paste the URL into the backend address, and save. The app writes it into `docs/config.js`. Commit and push it.

**Without the app:** on github.com open `docs/config.js`, click the pencil, and change the line:

```js
window.PORTAL_API = 'https://script.google.com/macros/s/XXXXXXXX/exec';
```

Then click **Commit changes**.

In the same file, `window.PORTAL_GRADES` lists the grade buttons on the sign-in page, for example `['5', '6', '7', '8', '9']`.

---

## Step 4 – Add the staff accounts

In the Sheet, open the **Users** tab and add one row for each Teacher or Admin:

| Name | PW | Role | Grade | Gender |
|---|---|---|---|---|
| Sarah Awad | *(a strong password)* | Teacher | NA | |

- Staff use Grade `NA` and sign in with the **Teacher / Admin** button. They see every test and the **Studio** card.
- **Students** create their own accounts on the sign-in page (**New student? Create an account**). They pick a grade, a name, a password, and Boy or Girl. You can still add, edit or delete students in this tab.
- **Arabic students:** a name that starts with `AR.` or contains `(AR)` sees only the Arabic tests (files ending in `-ar.html`).
- Keep the Sheet **private**. Passwords are stored as plain text.

---

## Step 5 – Let the iPad Studio publish (GitHub key)

You only need this step to publish from an iPad, a phone or any browser. The desktop app publishes with git and does not need it.

1. **Make a key on GitHub:** **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
   - **Repository access:** *Only select repositories* → `math-tests-studio`
   - **Permissions → Repository permissions → Contents:** *Read and write*
   - **Expiration:** choose a date and put a reminder in your calendar to renew it.
   - Click **Generate token** and copy it. GitHub shows it only once.
2. **Store it in Apps Script:** open **Project Settings** (gear icon) **→ Script properties → Add script property**, and add two properties:
   - `GITHUB_REPO` = `<your-user>/math-tests-studio`
   - `GITHUB_TOKEN` = the key you copied
3. **Test it:** in the editor, choose **`checkGitHub`** and click **Run**. Approve the new permission to connect to external services. The log should start with `OK:` and the repo name. An error names what to fix.
4. **Deploy a new version**, as in step 7.

How the key is protected:
- It is stored only in Apps Script, never in the website.
- It can change only this one repo.
- Only signed-in staff can publish.
- The backend refuses any page that still contains answers or scripts.

Never paste the key into chat, email or a file in the repo.

---

## Step 6 – Check that everything works

Open the site in a browser and check each item:

- [ ] **Backend is live:** open the `/exec` URL in a browser. It shows `{"ok":true,"app":"Math Tests Studio backend"}`.
- [ ] **Wrong sign-in is refused:** sign in with a made-up name. You see the "not correct" message.
- [ ] **Staff sign-in:** sign in with **Teacher / Admin**. You see the home screen and the **Studio** card.
- [ ] **Registration:** create a test student, for example `Test Student 1`. You go straight to the home screen, and a new row appears in **Users**. Delete the row afterwards.
- [ ] **A test:** publish a test (desktop app step 4, or Studio). Open it from the home screen, answer, and submit. You get a score with explanations, and a row appears in **Results**.
- [ ] **Studio** (if you did step 5): publishing a test from `studio.html` ticks all four steps, and `docs/GradeN/<name>.html` appears in the repo.
- [ ] **Phone:** open the site on a phone and check that the pages fit the screen.

---

## Step 7 – Updating later

| What changed | What to do |
|---|---|
| Website files in `docs/` | Commit and push to `main`. GitHub Pages updates in about 1 minute. Refresh the page; on an iPad you may need to close the tab first. |
| `backend/Code.gs` | Paste the new code into Apps Script and **Save**. Then **Deploy → Manage deployments → pencil (Edit) → Version: New version → Deploy**. The URL stays the same, so `config.js` does not change. |
| A test | Make the corrected file in Claude with the **same file name**, then import and publish it again. It keeps the same Test ID, so students' earlier results stay with it. |
| Hide a test | In the **Tests** tab, set **Active** to `No`. |
| Add a grade | Add it to `PORTAL_GRADES` in `docs/config.js`. The app creates the `docs/GradeN` folder. |
| Template change | `docs/studio/test-template.txt` must stay an exact copy of `app/src/main/resources/test-template.html`. |

Saving the code alone does **not** update the live backend. You must deploy a **New version**.

---

## Troubleshooting

| Problem | Likely cause and fix |
|---|---|
| Site shows 404 | Pages is not set to `main` / `/docs` (step 1), or it is still building. Wait 2 minutes. |
| "No connection" or "not connected to its database yet" at sign-in | `PORTAL_API` in `config.js` is wrong or missing, or the deployment access is not *Anyone*. |
| "Creating an account is not switched on yet" | The live backend is an older version. Paste the current `Code.gs` and deploy a **New version**. |
| Sign-in correct but refused | Check the spelling, Role and Grade in **Users**. Staff need Grade `NA`. Five wrong tries lock a name for 10 minutes. |
| "Run setup() first" / "Missing tab" | Run `setup()` in the Apps Script editor. |
| Studio: "Publishing from the browser is not set up yet" | `GITHUB_REPO` or `GITHUB_TOKEN` is missing. Add them (step 5), run `checkGitHub()`, and deploy a new version. |
| Studio: "GitHub did not accept the token" / "cannot write to the repo" | The key expired or lacks *Contents: Read and write*. Make a new key and replace `GITHUB_TOKEN`. |
| Test does not appear for students | Check its row in **Tests**: correct grade, **Active** not `No`. Arabic users see only `-ar` files, and other users never see them. |
| Changes do not show | The browser is caching. Hard-refresh (Ctrl+F5), or wait a minute for Pages. |

---

## Limits

- The sign-in is a gate, not strong security. Passwords are plain text in the Sheet, so keep it private.
- Test pages are public files, but they contain no answers. Grading happens in the backend.
- A test opened as a local file shows its questions but cannot be graded.
