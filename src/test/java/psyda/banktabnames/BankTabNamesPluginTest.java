package psyda.banktabnames;

import psyda.banktabnames.BankTabNamesPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BankTabNamesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankTabNamesPlugin.class);
		RuneLite.main(args);
	}
}