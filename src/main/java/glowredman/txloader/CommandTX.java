package glowredman.txloader;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.ClientCommandHandler;

import glowredman.txloader.Asset.Source;

class CommandTX implements ICommand {

    @Override
    public int compareTo(Object o) {
        return this.getCommandName().compareTo(((ICommand) o).getCommandName());
    }

    @Override
    public String getCommandName() {
        return "tx";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tx <save|add <version> <source> <resourceLocation> [resourceLocationOverride] [force]>";
    }

    @Override
    public List<String> getCommandAliases() {
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        int length = args.length;
        if (length == 0) {
            sender.addChatMessage(getColoredText("Not enough Arguments!", EnumChatFormatting.RED));
            return;
        }
        String mode = args[0];
        if (mode.equals("save")) {
            if (ConfigHandler.save()) {
                sender.addChatMessage(
                        getColoredText(
                                "Config saved successfully. The Changes take effect after restarting the Game.",
                                EnumChatFormatting.GREEN));
                return;
            }
            sender.addChatMessage(
                    getColoredText("Failed saving Config! Look at the Log to find the Cause.", EnumChatFormatting.RED));
            return;
        }
        if (mode.equals("add")) {
            if (length < 4) {
                sender.addChatMessage(getColoredText("Not enough Arguments!", EnumChatFormatting.RED));
                return;
            }
            args = fixSpacesForVersion(args);
            if (length == 0) {
                sender.addChatMessage(getColoredText("Missing closing Quotation Mark!", EnumChatFormatting.RED));
                return;
            }
            if (length > 6) {
                sender.addChatMessage(
                        getColoredText(
                                "Too many Arguments! If your Version has Spaces, wrap it in Quotation Marks.",
                                EnumChatFormatting.RED));
                return;
            }
            final Asset asset = new Asset(args[3], args[1], Source.get(args[2]));
            if (length != 4) {
                String arg4 = args[4];
                if (length == 5) {
                    // argument 4 may either be forceLoad or resourceLocationOverride
                    if (arg4.equals("true")) {
                        asset.forceLoad = true;
                    } else if (arg4.equals("false")) {
                        asset.forceLoad = false;
                    } else {
                        asset.resourceLocationOverride = arg4;
                    }
                } else {
                    asset.resourceLocationOverride = arg4;
                    asset.forceLoad = args[5].equals("true");
                }
            }
            TXLoaderCore.REMOTE_ASSETS.add(asset);
            sender.addChatMessage(getColoredText("Done. Don't forget to save!", EnumChatFormatting.GREEN));
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        int length = args.length;
        if (length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "add", "save");
        }
        if (!args[0].equals("add") || length > 6) {
            return null;
        }
        if (length == 2) {
            return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, RemoteHandler.VERSIONS.keySet());
        }
        if (length == 3) {
            return CommandBase.getListOfStringsFromIterableMatchingLastWord(args, Source.NAMES);
        }
        if (length == 5 || length == 6) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "true", "false");
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    private static IChatComponent getColoredText(String text, EnumChatFormatting color) {
        return new ChatComponentText(text).setChatStyle(new ChatStyle().setColor(color));
    }

    /**
     * Some Minecraft versions have spaces in their name (e.g. <code>1.14.2 Pre-Release 4</code>). The later part of
     * {@link CommandTX#processCommand(ICommandSender, String[])} assumes the version to only be in one element of the
     * <code>args</code>-array. Minecrafts splits the entered command at the spaces
     * 
     * @param args the command arguments, as passed to {@link CommandTX#processCommand(ICommandSender, String[])}
     * @return <b>args</b> if no version wrapping was done<br>
     *         <b>args</b> with all version elements joined together, if valid version wrapping was done<br>
     *         an empty {@link String} array, if invalid wrapping was done
     * @author glowredman
     * @see CommandHandler#executeCommand(ICommandSender, String)
     * @see ClientCommandHandler#executeCommand(ICommandSender, String)
     */
    private static String[] fixSpacesForVersion(String[] args) {

        // return early, if no wrapping is applied
        if (!args[1].startsWith("\"")) {
            return args;
        }

        // find last element
        int end = 0;
        for (int i = 1; i < args.length; i++) {
            if (args[i].endsWith("\"")) {
                end = i;
                break;
            }
        }

        // return early if no closing quotation mark was found
        if (end == 0) {
            return new String[0]; // invalid wrapping
        }

        int offset = end - 1;
        String[] fixedArgs = new String[args.length - offset];

        // join version elements
        StringBuilder sb = new StringBuilder();
        sb.append(args[1]);
        for (int i = 2; i <= end; i++) {
            sb.append(' ');
            sb.append(args[i]);
        }

        // remove quotation marks
        sb.deleteCharAt(0);
        sb.deleteCharAt(sb.length() - 1);

        // copy other elements
        fixedArgs[0] = args[0];
        fixedArgs[1] = sb.toString();
        for (int i = end + 1; i < args.length; i++) {
            fixedArgs[i - offset] = args[i];
        }

        return fixedArgs;
    }
}
