# InteractiveMessengerTransifex
Integration with the Transifex collaborative translation website for InteractiveMessenger files.
- Upload your language file to Transifex
- Download translation files from Transifex
- Reformat language file for uploading correctly

## Information
* **Build server:** http://jenkins.wiefferink.me/job/InteractiveMessengerTransifex/
* **Javadocs:** https://wiefferink.me/InteractiveMessengerTransifex/javadocs

## Usage
1. Download the executable `InteractiveMessengerTransifex.jar` file.
1. Run the `InteractiveMessengerTransifex.jar` file: `java -jar InteractiveMessengerTransifex.jar <command...>`

## Commands
#### `help`
Show possible commands.

#### `download <apitoken> <project> <resource> <to> [translatedFor]`
Download translations.
- `<apitoken>`: Credentials of your Transifex account as `<username>/<password>` or an API token as `1/<apitoken>`
- `<project>`: Slug of the project to download from.
- `<resource>`: Slug of the resource to download from.
- `<to>`: Path of the directory to save the downloaded translations to.
- `[translatedFor]`: (optional) Only download translations for languages that are translated for at least this percentage. 

#### `formatForUpload <in> <out> <languagecode>`
Format a language for to get ready for upload.
- `<in>`: File to read from (typically from your git repository of your project that uses InteractiveMessenger)
- `<out>`: File to save to before upload (could be some temporary location).
- `<languagecode>`: The languagecode to upload this file to in Transifex (most likely `en` for English)

#### `upload <apitoken> <project> <resource> <from>` 
Upload a language file (if any source strings are not in this file, but are on Transifex, these will get lost!)
- `<apitoken>`: Credentials of your Transifex account as `<username>/<password>` or an API token as `1/<apitoken>`
- `<project>`: Slug of the project to download from.
- `<resource>`: Slug of the resource to download from.
- `<from>`: File to upload (could be a file that formatForUpload has written to).