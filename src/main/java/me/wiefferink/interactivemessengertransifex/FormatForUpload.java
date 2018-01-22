package me.wiefferink.interactivemessengertransifex;

import com.google.common.base.Charsets;
import me.wiefferink.interactivemessenger.Log;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class FormatForUpload {

	public static final String help = "<in> <out> <languageCode>";

	public static boolean run(String[] args) {

		// Print call information
		Log.info("Formatting language file for Transifex, args:");
		for(String arg : args) {
			Log.info("  "+arg);
		}

		// Check number of arguments
		if(args.length < 3) {
			Log.error("  Wrong number of arguments:", help);
			return false;
		}

		// Check input
		File input = new File(args[0]);
		if(!input.exists()) {
			Log.error("  Input does not exist:", input.getAbsolutePath());
			return false;
		}
		if(!input.isFile()) {
			Log.error("  Input is not a file:", input.getAbsolutePath());
			return false;
		}

		// Check output
		File output = new File(args[1]);
		if(output.exists() && !output.isFile()) {
			Log.error("  Output exists but is not a file:", output.getAbsolutePath());
			return false;
		}
		if(!output.getParentFile().exists() && !output.getParentFile().mkdirs()) {
			Log.error("  Failed to create directories leading to output:", output.getAbsolutePath());
			return false;
		}

		// Check languagecode
		String languageCode = args[2];
		if(languageCode.isEmpty()) {
			Log.error("  Languagecode is empty");
			return false;
		}


		try (
				InputStreamReader inputReader = new InputStreamReader(new FileInputStream(input), Charsets.UTF_8)
		) {
			YamlConfiguration inputConfig = YamlConfiguration.loadConfiguration(inputReader);
			YamlConfiguration outputConfig = new YamlConfiguration();

			for(String key : inputConfig.getKeys(false)) {
				if(inputConfig.isList(key)) {
					List<String> list = inputConfig.getStringList(key);
					StringBuilder combined = new StringBuilder();
					for(int i=0; i<list.size(); i++) {
						String doing = list.get(i);
						if(i > 0) {
							combined.append("\n");
						}

						// Replace indentation with tabs, Transifex gets rid of spaces otherwise (this assumes 4 spaces for a tab)
						while(doing.startsWith("    ")) {
							doing = "\t"+doing.substring(4);
						}
						combined.append(doing);
					}

					outputConfig.set(languageCode + "." + key, combined.toString());
				} else if(inputConfig.isString(key)) {
					outputConfig.set(languageCode+"."+key, inputConfig.getString(key));
				} else {
					Log.warn("  Unsupported section at", key, "in inputfile:", input.getAbsolutePath());
				}
			}


			outputConfig.save(output);
		} catch (IOException e) {
			Log.error("  Failed to read/write file:", ExceptionUtils.getStackTrace(e));
			return false;
		}

		Log.info("  Formatting completed");
		return true;
	}
}
