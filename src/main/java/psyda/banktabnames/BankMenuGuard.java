package psyda.banktabnames;

/**
 * Controls how the plugin treats the game's risky bank tab right-click options
 * (Collapse, Remove-placeholders), which sit close to the plugin's own entries
 * and are easy to click by accident.
 */
public enum BankMenuGuard
{
	OFF("Off"),
	SHIFT("Show only on Shift"),
	HIDE("Hide entirely");

	private final String label;

	BankMenuGuard(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}