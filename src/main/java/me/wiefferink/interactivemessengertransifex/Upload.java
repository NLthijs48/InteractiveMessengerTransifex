package me.wiefferink.interactivemessengertransifex;

import com.google.common.base.Charsets;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import me.wiefferink.interactivemessenger.Log;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Upload {

	public static String help = "<apitoken> <project> <resource> <from>";

	public static boolean run(String[] args) {
		Log.info("Uploading resource from Transifex");

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

		File from = new File(args[3]);
		Log.info("  Project:", project);
		Log.info("  Resource:", resource);
		Log.info("  File:", from.getAbsolutePath());

		// Check from
		if(!from.exists() || !from.isFile()) {
			Log.error("  Input does not exist or is not a file");
			return false;
		}


		// Check if from is suitable
		try (InputStreamReader inputReader = new InputStreamReader(new FileInputStream(from), Charsets.UTF_8)) {
			YamlConfiguration fromConfig = YamlConfiguration.loadConfiguration(inputReader);

			// Check if it has a single top-level key
			int topLevelKeys = fromConfig.getKeys(false).size();
			if(topLevelKeys != 1) {
				Log.error("  Input does not have a single top-level key (which is the language), it has", topLevelKeys, "keys");
				return false;
			}

			// Check for at least 1 translation string
			int translatables = 0;
			for(String topKey : fromConfig.getKeys(false)) {
				ConfigurationSection topSection = fromConfig.getConfigurationSection(topKey);
				if(topSection == null) {
					Log.error("  Top-level key of the input is not a ConfigurationSection");
					return false;
				}
				translatables += topSection.getKeys(false).size();
			}
			if(translatables == 0) {
				Log.error("  There are no translation string in the input, which would wipe all translations on Transifex");
				return false;
			}
		} catch(IOException e) {
			Log.error("  Failed to read input as YAML file");
			return false;
		}

		try {
			// Get list of languages for EN.yml
			HttpResponse<String> uploadResponse = Unirest.put("https://www.transifex.com/api/2/project/{project}/resource/{resource}/content")
					.basicAuth("api", apitoken)
					.routeParam("project", project)
					.routeParam("resource", resource)
					.field("file", from)
					.asString();
			if(uploadResponse.getStatus() != 200) {
				Log.error("  Uploading source strings failed:", uploadResponse.getStatus(), uploadResponse.getStatusText(), uploadResponse.getBody());
				return false;
			}

			Log.info("  Uploaded source strings successfully");
		} catch(UnirestException e) {
			Log.error("  Failed:", ExceptionUtils.getStackTrace(e));
			return false;
		}


		return true;
	}
}
