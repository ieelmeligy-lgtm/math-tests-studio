# Math Tests Studio

*Courtesy of Miss Sarah Awad – Math Teacher*

A Windows desktop app that publishes self-grading multiple-choice maths tests, in English or Arabic, to a website for students. You make the test in Claude from photos of the textbook. The app imports the HTML file, removes the answers from the page, and publishes it.

| Folder | What it is |
|---|---|
| `app/` | Java 21 desktop app (Swing + FlatLaf). It imports the test files and uses git to publish. It never calls Claude, so no API key is needed. |
| `docs/` | The website, served by GitHub Pages: sign-in, a home screen with progress and stars, tests in `GradeN/`, and the Studio page (`studio.html`) for publishing from an iPad or any browser. |
| `backend/Code.gs` | Google Apps Script for the Google Sheet that holds users, tests, the answer key and results. |

## How it works

1. **Make the test in Claude.** Use Claude Code or claude.ai with the *math-tests-portal* skill. Attach the photos and paste the request from the app's **Copy the request for Claude** button. Download the HTML file into `docs\GradeN\` of your local copy of this repo. The file name becomes the page address, so use letters, digits and dashes, for example `fractions-unit-2.html`.
2. **Site folder** (step 1, only once). Choose your local clone of this repo.
3. **Import test** (step 2). The app lists the new files it finds in the grade folders and reads each one:
   - the questions, options and section headings
   - the correct answers (`data-c`) and the explanations
   - the language: Arabic pages are shown right to left with the letters أ ب ج د
4. **Review test** (step 3). Check and edit every question, then preview it. In the preview, Submit grades on your PC so you can check the answers. Then approve.
5. **Publish & deploy** (step 4).
   - The page is rebuilt **without the answers** and saved over the downloaded file. The file with the answers is kept privately in `%APPDATA%\MathTestsStudio\originals\`.
   - The page is committed and pushed to GitHub.
   - The test row and answer key are written to the Sheet.
   - The app waits until the page is live.

The connection lights show the status of Internet, Test files, GitHub repo, Backend, Google Sheet and Live website. **ONLINE** appears when all six are green.

**The answer key never goes online in the HTML.** Students submit their choices, and the Apps Script grades them against the `Questions` tab, saves the result, and sends back the score and explanations. There are two safety checks:
- The app refuses to commit a page that still contains answers.
- A git pre-commit hook (`.githooks/pre-commit`, turned on by the app) also stops commits made with other tools, such as VS Code.

To change a published test, download the corrected file from Claude into the same grade folder with the same name, then import and publish it again. It keeps the same Test ID, so students' earlier results stay attached to it. You can also import a published test again: the app opens the private copy it kept.

## On iPad, phone or any browser (Studio page)

The website has its own Studio, so no laptop is needed. It does the same as the desktop app.

1. In the **Claude app**, attach the photos, paste the request, and save the HTML file to the **Files** app.
2. In **Safari**, open the site and sign in with **Teacher / Admin**. Tap **Studio** on the home screen, or open `studio.html`.
3. **Import:** tap **Choose the test file** → **Browse** → pick the file. The language, questions and grade are read from it.
4. **Review:** edit questions and answers. The preview grades in the browser only.
5. **Publish:**
   - The page is rebuilt without the answers.
   - The Apps Script saves the page to GitHub (`docs/GradeN/<name>.html`) and the answer key to the Sheet.
   - The Studio waits until the test is live.

Safari cannot run git, so on the web it is the **Apps Script** that saves the page to GitHub. It needs a GitHub key once:

1. On github.com go to **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
   - **Repository access:** *Only select repositories* → `math-tests-studio`
   - **Permissions → Contents:** *Read and write*
   - Pick an expiry date and remember to renew the key before then.
2. In Apps Script open **Project Settings → Script properties** and add:
   - `GITHUB_REPO` = `your-user/math-tests-studio`
   - `GITHUB_TOKEN` = the key
3. In the editor, run `checkGitHub()` once and approve the request to connect to GitHub. The log should say `OK`.
4. Choose **Deploy → Manage deployments → Edit → Version: New version** so the web app uses the new code.

How the key is protected:
- It is stored only inside the Apps Script, never in the website.
- It can change only this one repo.
- Only a signed-in Teacher or Admin can publish.
- The Apps Script refuses any page that still contains answers, was not built by the Studio, or contains scripts or links in the questions.
- Commits are made as "Math Tests Studio" with your private GitHub noreply address.

`docs/studio/test-template.txt` is a copy of `app/src/main/resources/test-template.html`. If you change one, copy it to the other so both make the same page.

## Format the app reads

This is the skill's standard test page:
- question cards (`div.card`) containing `.stem`, radio options in `label.ch` → `.opt`, the correct option marked `data-c`, and the explanation in `.why`
- optional `h2` section headings
- optional `data-note="…"` on a card where a misprint was fixed, and `data-options="claude"` where Claude wrote the options

Questions without options are left out and listed. Only the maths markup the site has styles for is kept (fractions, roots, Venn diagrams, simple SVG drawings and similar). Scripts, links and styles are removed.

## One-time setup

1. **Google Sheet.** Create a new Google Sheet. Open **Extensions → Apps Script** and replace the code with `backend/Code.gs`. Run `setup()` once and approve the access request. Then choose **Deploy → New deployment → Web app**, set **Execute as** to *Me* and **Who has access** to *Anyone*, and copy the `/exec` URL.
2. **Users.** In the `Users` tab add yourself as `Name | PW | Role | Grade`, for example `Sarah Awad | ... | Teacher | NA`. Add students with Role `Student` and their grade.
3. **App settings.** Start the app with `Start-MathTestsStudio.cmd` and open **Settings**. Enter:
   - the `/exec` URL (it is saved into `docs/config.js`)
   - the Teacher or Admin name and password from step 2
4. **GitHub Pages.** In the repo, open **Settings → Pages** and set the source to **Deploy from a branch**, branch `main`, folder `/docs`.

For later backend changes, choose **Deploy → Manage deployments → Edit → Version: New version**. This keeps the same URL.

## Limits

- The sign-in is a gate, not strong security. Passwords are plain text in the Sheet, so keep the Sheet private.
- A test opened as a local file shows its questions but cannot grade them, because grading needs the backend.
- The site's sign-in and home screens are in English. The test pages themselves can be English or Arabic.
