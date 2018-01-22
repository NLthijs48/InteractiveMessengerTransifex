package me.wiefferink.interactivemessengertransifex;

import com.google.common.base.Charsets;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import me.wiefferink.interactivemessenger.Log;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Download {

	public static String help = "<apitoken> <project> <resource> <to> [translatedFor] [format] [header] [version]";

	public static boolean run(String[] args) {
		Log.info("Downloading translations from Transifex");

		// Check args
		if(args.length < 4) {
			Log.error("Not enough arguments:", help);
			return false;
		}

		// API token
		String apitoken = args[0];

		// Project
		String project = args[1];

		// Resource
		String resource = args[2];

		// Check to
		File to = new File(args[3]);
		if(to.exists() && !to.isDirectory()) {
			Log.error("Output directory exists but is not a directory:", to.getAbsolutePath());
			return false;
		}
		if(!to.exists() && !to.mkdirs()) {
			Log.error("Failed to create output directory:", to.getAbsolutePath());
			return false;
		}

		// Parse translated percentage
		int translatedFor = 0;
		if(args.length > 4) {
			try {
				translatedFor = Integer.parseInt(args[4]);
			} catch(NumberFormatException e) {
				Log.error("Translated for percentage argument is not a number:", args[4]);
				return false;
			}
		}

		// Format for use in a project or not
		boolean format = false;
		if(args.length > 5) {
			format = "true".equalsIgnoreCase(args[5]);
		}

		// Header
		List<String> header = new ArrayList<>();
		if(args.length > 6) {
			try {
				header = Files.readAllLines(Paths.get(args[6]));
			} catch(IOException e) {
				Log.error("Failed to read header from:", Paths.get(args[6]).getFileName());
				return false;
			}
		}

		// Version code
		String versionCode = "";
		if(args.length > 7) {
			versionCode = args[7];
		}

		// Track if everything went completely correct (but still make an attempt to download as much as possible)
		boolean success = true;
		try {
			// Get list of languages for EN.yml
			Log.info("Downloading languages list");
			HttpResponse<JsonNode> languagesResponse = Unirest.get("https://www.transifex.com/api/2/project/{project}/resource/{resource}/?details")
					.basicAuth("api", apitoken)
					.routeParam("project", project)
					.routeParam("resource", resource)
					.asJson();
			if(languagesResponse.getStatus() != 200) {
				Log.error("Requesting language list failed:", languagesResponse.getStatus(), languagesResponse.getStatusText(), languagesResponse.getBody());
				return false;
			}
			JSONObject languages = languagesResponse.getBody().getObject();

			// Get languages stats (to determine if translated enough)
			Log.info("Downloading language stats");
			HttpResponse<JsonNode> languageStatsResponse = Unirest.get("https://www.transifex.com/api/2/project/{project}/resource/{resource}/stats")
					.basicAuth("api", apitoken)
					.routeParam("project", project)
					.routeParam("resource", resource)
					.asJson();
			if(languageStatsResponse.getStatus() != 200) {
				Log.error("Requesting language stats failed:", languageStatsResponse.getStatus(), languageStatsResponse.getStatusText(), languageStatsResponse.getBody());
				return false;
			}
			JSONObject languagesStats = languageStatsResponse.getBody().getObject();

			// Find the source language
			String sourceLanguageCode = languages.getString("source_language_code");
			if(sourceLanguageCode == null || sourceLanguageCode.isEmpty()) {
				Log.error("Could not determine the source language, got an empty language code from Transifex");
				return false;
			}
			String sourceLanguageName = null;
			for(Object language : languages.getJSONArray("available_languages")) {
				if(sourceLanguageCode.equalsIgnoreCase(((JSONObject)language).getString("code"))) {
					sourceLanguageName = ((JSONObject)language).getString("name");
				}
			}
			Log.info("Source language:", sourceLanguageName, "("+sourceLanguageCode+")");

			// Clean up old language files
			if(format) {
				File[] oldFiles = to.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
				if(oldFiles != null) {
					for(File oldFile : oldFiles) {
						// Don't remove/update the source file
						if(oldFile.getName().equalsIgnoreCase((sourceLanguageCode+".yml"))) {
							continue;
						}
						if(!oldFile.delete()) {
							Log.error("Could not delete old language file:", oldFile.getAbsolutePath());
						}
					}
				}
			}

			// Handle each language
			Log.info("Starting download:");
			for(Object language : languages.getJSONArray("available_languages")) {
				JSONObject languageObject = (JSONObject) language;
				String languageName = languageObject.getString("name");
				String languageCode = languageObject.getString("code");
				Log.info("  "+languageName+" ("+languageCode+"):");
				if(format && sourceLanguageCode.equalsIgnoreCase(languageCode)) {
					Log.info("    is the source language, skipping");
					continue;
				}

				JSONObject languageStats = languagesStats.getJSONObject(languageCode);
				int translated = languageStats.getInt("translated_entities");
				int untranslated = languageStats.getInt("untranslated_entities");
				double translatedPercent = ((double) translated) / (translated + untranslated) * 100;

				Log.info("    translated:", translated);
				Log.info("    untranslated:", untranslated);
				Log.info("    percentage:", translatedPercent);

				if(translatedPercent < translatedFor) {
					Log.info("    skipped, not translated enough");
					continue;
				}

				HttpResponse<String> translationsResponse = Unirest.get("https://www.transifex.com/api/2/project/{project}/resource/{resource}/translation/{language}/?mode=default&file")
						.basicAuth("api", apitoken)
						.routeParam("project", project)
						.routeParam("resource", resource)
						.routeParam("language", languageCode)
						.asString();

				// Write raw file if formatting is disabled
				if(!format) {
					File fileOutput = new File(to, languageName + ".yml");

					// Write as UTF8
					try(Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOutput), Charsets.UTF_8))) {
						out.write(translationsResponse.getBody());
					} catch(IOException e) {
						Log.error("    Failed to write raw output:", fileOutput.getAbsolutePath(), ExceptionUtils.getStackTrace(e));
						success = false;
						continue;
					}
					Log.info("    saved raw to:", fileOutput.getAbsolutePath());
					continue;
				}

				// Format the file
				YamlConfiguration formatted = new YamlConfiguration();
				YamlConfiguration unformatted = new YamlConfiguration();
				try {
					unformatted.loadFromString(translationsResponse.getBody());
				} catch(InvalidConfigurationException e) {
					Log.error("    Result from transifex is not a valid YAML file:", e);
					success = false;
					continue;
				}

				// Check for single root key
				if(unformatted.getKeys(false).size() != 1) {
					Log.error("    Result did not have a single root key as expected, keys:", unformatted.getKeys(false));
					success = false;
					continue;
				}
				String transifexLanguage = new ArrayList<>(unformatted.getKeys(false)).get(0);
				File fileOutput = new File(to, transifexLanguage.toUpperCase() + ".yml");

				// Get section
				ConfigurationSection messagesSection = unformatted.getConfigurationSection(transifexLanguage);
				if(messagesSection == null) {
					Log.error("    The top level key", transifexLanguage, "does not map to a ConfigurationSection, type:", unformatted.get(transifexLanguage).getClass().getName());
					success = false;
					continue;
				}

				for(String messageKey : messagesSection.getKeys(false)) {
					String message = messagesSection.getString(messageKey);
					if(message == null) {
						Object value = messagesSection.get(messageKey);
						Log.warn("    Key", messageKey, "does not have a string as value, type:", value!=null ? value.getClass().getName() : null);
						success = false;
						continue;
					}

					String[] messageLines = message.split("\n");
					for(int i=0; i<messageLines.length; i++) {
						messageLines[i] = messageLines[i].replace("\t", "    ");
					}
					if(messageLines.length == 1) {
						formatted.set(messageKey, messageLines[0]);
					} else {
						formatted.set(messageKey, Arrays.asList(messageLines));
					}
				}

				// Output Yaml and keep UTF-8 characters
				DumperOptions yamlOptions = new DumperOptions();
				yamlOptions.setIndent(2);
				yamlOptions.setAllowUnicode(true);
				yamlOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
				yamlOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
				yamlOptions.setWidth(999);

				Representer yamlRepresenter = new YamlRepresenter();
				yamlRepresenter.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
				yamlRepresenter.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);

				Yaml yaml = new Yaml(new YamlConstructor(), yamlRepresenter, yamlOptions);

				String outputString = yaml.dump(formatted.getValues(false));

				// Add header
				String languageHeader = StringUtils.join(header, "\n")
						.replace("{language}", languageName)
						.replace("{languageCode}", languageCode)
						.replace("{translated}", translated+"")
						.replace("{untranslated}", untranslated+"")
						.replace("{translatedPercent}", Math.round(translatedPercent)+"")
						.replace("{messages}", (translated+untranslated)+"")
						.replace("{version}", versionCode)
						.replace("{source}", sourceLanguageCode.equalsIgnoreCase(languageCode) ? ", source language" : "");
				outputString = languageHeader + "\n" + outputString;

				// Write as UTF8
				try(Writer out = new OutputStreamWriter(new FileOutputStream(fileOutput), Charsets.UTF_8)) {
					out.write(outputString);
				} catch(IOException e) {
					Log.error("    Failed to write formatted output:", fileOutput.getAbsolutePath(), ExceptionUtils.getStackTrace(e));
					success = false;
					continue;
				}
				Log.info("    saved formatted to:", fileOutput.getAbsolutePath());
			}
		} catch(UnirestException e) {
			Log.error("Failed:", ExceptionUtils.getStackTrace(e));
			return false;
		}

		return success;
	}
}
