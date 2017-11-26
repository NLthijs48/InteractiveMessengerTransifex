package me.wiefferink.interactivemessengertransifex;

import com.google.common.base.Charsets;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import me.wiefferink.interactivemessenger.Log;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class Download {

	public static String help = "<apitoken> <project> <resource> <to> [translatedFor]";

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

		try {
			// Get list of languages for EN.yml
			HttpResponse<JsonNode> languages = Unirest.get("https://www.transifex.com/api/2/project/{project}/resource/{resource}/?details")
					.basicAuth("api", apitoken)
					.routeParam("project", project)
					.routeParam("resource", resource)
					.asJson();
			if(languages.getStatus() != 200) {
				Log.error("Requesting language list failed:", languages.getStatus(), languages.getStatusText(), languages.getBody());
				return false;
			}

			// Get languages stats (to determine if translated enough)
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

			// Handle each language
			for(Object language : languages.getBody().getObject().getJSONArray("available_languages")) {
				JSONObject languageObject = (JSONObject) language;
				String languageName = languageObject.getString("name");
				String languageCode = languageObject.getString("code");
				Log.info("  "+languageName+" ("+languageCode+"):");

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

				File fileOutput = new File(to, languageName+".yml");

				// Write as UTF8
				try(Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOutput), Charsets.UTF_8))){
					out.write(translationsResponse.getBody());
				} catch(IOException e) {
					Log.error("    Failed to write output:", fileOutput.getAbsolutePath(), ExceptionUtils.getStackTrace(e));
					return false;
				}
				Log.info("    saved to:", fileOutput.getAbsolutePath());
			}


		} catch(UnirestException e) {
			Log.error("Failed:", ExceptionUtils.getStackTrace(e));
			return false;
		}


		return true;
	}
}
