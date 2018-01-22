package me.wiefferink.interactivemessengertransifex;

import me.wiefferink.interactivemessenger.Log;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;

public class TransifexTools {

	public static final String help = StringUtils.join(Arrays.asList(
			"help",
			Download.help,
			FormatForUpload.help,
			Upload.help
	), "\n");

	public static void main(String[] args) {
		if(args.length == 0) {
			Log.error("Specify the command to run: \n"+help);
			System.exit(1);
			return;
		}

		String[] commandArgs = new String[args.length-1];
		System.arraycopy(args, 1, commandArgs, 0, args.length-1);
		boolean result = true;
		if("help".equalsIgnoreCase(args[0])) {
			Log.info(help);
		} else if("formatForUpload".equalsIgnoreCase(args[0])) {
			result = FormatForUpload.run(commandArgs);
		} else if("download".equalsIgnoreCase(args[0])) {
			result = Download.run(commandArgs);
		} else if("upload".equalsIgnoreCase(args[0])) {
			result = Upload.run(commandArgs);
		} else {
			Log.error("Incorrect command:", args[0], ", use one of the following:\n"+help);
			result = false;
		}

		if(!result) {
			System.exit(1);
		}
	}
}
