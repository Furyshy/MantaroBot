/*
 * Copyright (C) 2016-2020 David Rubio Escares / Kodehawa
 *
 *  Mantaro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  Mantaro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro.  If not, see http://www.gnu.org/licenses/
 */

package net.kodehawa.mantarobot.commands;

import com.github.natanbc.usagetracker.DefaultBucket;
import com.google.common.eventbus.Subscribe;
import lavalink.client.io.LavalinkSocket;
import lavalink.client.io.RemoteStats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.kodehawa.mantarobot.commands.currency.TextChannelGround;
import net.kodehawa.mantarobot.commands.info.stats.CategoryStatsManager;
import net.kodehawa.mantarobot.commands.info.stats.CommandStatsManager;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.command.processor.CommandProcessor;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.AliasCommand;
import net.kodehawa.mantarobot.core.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.core.modules.commands.SimpleTreeCommand;
import net.kodehawa.mantarobot.core.modules.commands.SubCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandCategory;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandPermission;
import net.kodehawa.mantarobot.core.modules.commands.base.Context;
import net.kodehawa.mantarobot.core.modules.commands.base.ITreeCommand;
import net.kodehawa.mantarobot.core.modules.commands.help.HelpContent;
import net.kodehawa.mantarobot.core.modules.commands.i18n.I18nContext;
import net.kodehawa.mantarobot.data.I18n;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.DiscordUtils;
import net.kodehawa.mantarobot.utils.RatelimitUtils;
import net.kodehawa.mantarobot.utils.StringUtils;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.CustomFinderUtil;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import net.kodehawa.mantarobot.utils.commands.FinderUtils;
import net.kodehawa.mantarobot.utils.commands.ratelimit.IncreasingRateLimiter;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.kodehawa.mantarobot.commands.info.AsyncInfoMonitor.*;
import static net.kodehawa.mantarobot.commands.info.HelpUtils.forType;
import static net.kodehawa.mantarobot.utils.commands.EmoteReference.BLUE_SMALL_MARKER;

@Module
public class InfoCmds {
    private final CategoryStatsManager categoryStatsManager = new CategoryStatsManager();

    @Subscribe
    public void donate(CommandRegistry cr) {
        cr.register("donate", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                ctx.sendLocalized("commands.donate.beg", EmoteReference.HEART,
                        ctx.getLanguageContext().get("commands.donate.methods")
                                .formatted("https://patreon.com/mantaro", "https://paypal.me/kodemantaro")
                );
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Shows the donation methods in case you want to support Mantaro.")
                        .build();
            }
        });
    }

    @Subscribe
    public void language(CommandRegistry cr) {
        cr.register("lang", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                ctx.sendLocalized("commands.lang.info", EmoteReference.ZAP,
                        String.join(", ", I18n.LANGUAGES).replace(".json", "")
                );
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Shows how to change the server and user languages, along with a language list.")
                        .build();
            }
        });

        cr.registerAlias("lang", "language");
    }

    @Subscribe
    public void avatar(CommandRegistry cr) {
        cr.register("avatar", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                ctx.findMember(content, ctx.getMessage()).onSuccess(members -> {
                    var member = CustomFinderUtil.findMemberDefault(content, members, ctx, ctx.getMember());
                    if (member == null) {
                        return;
                    }

                    var languageContext = ctx.getLanguageContext();
                    var user = member.getUser();
                    var embed = new EmbedBuilder()
                            .setAuthor(
                                    languageContext.get("commands.avatar.result").formatted(user.getName()),
                                    null, user.getEffectiveAvatarUrl()
                            )
                            .setColor(Color.PINK)
                            .setImage(user.getEffectiveAvatarUrl() + "?size=1024")
                            .setFooter(languageContext.get("commands.avatar.footer"), user.getEffectiveAvatarUrl());

                    ctx.send(embed.build());
                });
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Get a user's avatar URL.")
                        .setUsage("`~>avatar [@user]` - Returns the requested avatar URL")
                        .addParameter("@user",
                                "The user you want to check the avatar URL of. Can be a mention, or name#discrim")
                        .build();
            }
        });
    }

    @Subscribe
    public void guildinfo(CommandRegistry cr) {
        cr.register("serverinfo", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                var guild = ctx.getGuild();
                var guildData = ctx.getDBGuild().getData();

                var roles = guild.getRoles().stream()
                        .filter(role -> !guild.getPublicRole().equals(role))
                        .map(Role::getName)
                        .collect(Collectors.joining(", "));

                var owner = guild.getOwner();
                //This is wank lol
                if (owner == null) {
                    owner = guild.retrieveOwner(false).complete();
                }

                var languageContext = ctx.getLanguageContext();
                var str = """
                        **%1$s**
                        %2$s **%3$s:** %4$s
                        %2$s **%5$s:** %6$s
                        %2$s **%7$s:** %8$s
                        %2$s **%9$s:** %10$s
                        %2$s **%11$s:** %12$s
                        """.formatted(languageContext.get("commands.serverinfo.description").formatted(guild.getName()),
                        BLUE_SMALL_MARKER,
                        languageContext.get("commands.serverinfo.users"),
                        guild.getMemberCount(),
                        languageContext.get("commands.serverinfo.channels"),
                        "%,d / %,d".formatted(guild.getVoiceChannels().size(), guild.getTextChannels().size()),
                        languageContext.get("commands.serverinfo.owner"),
                        owner.getUser().getAsTag(),
                        languageContext.get("commands.serverinfo.region"),
                        guild.getRegion() == Region.UNKNOWN ?
                                languageContext.get("general.unknown") :
                                guild.getRegion().getName(),
                        languageContext.get("commands.serverinfo.created"),
                        Utils.formatDate(guild.getTimeCreated(), guildData.getLang())
                );

                ctx.send(new EmbedBuilder()
                        .setAuthor(languageContext.get("commands.serverinfo.header"), null, guild.getIconUrl())
                        .setColor(guild.getOwner().getColor() == null ? Color.PINK: guild.getOwner().getColor())
                        .setDescription(str)
                        .setThumbnail(guild.getIconUrl())
                        .addField(
                                languageContext.get("commands.serverinfo.roles").formatted(guild.getRoles().size()),
                                StringUtils.limit(roles, 500), false
                        )
                        .setFooter(languageContext.get("commands.serverinfo.id_show").formatted(guild.getId()), null)
                        .build()
                );
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("See your server's current stats.")
                        .build();
            }
        });

        cr.registerAlias("serverinfo", "guildinfo");
    }

    private void buildHelp(Context ctx, CommandCategory category) {
        var dbGuild = ctx.getDBGuild();
        var guildData = dbGuild.getData();
        var dbUser = ctx.getDBUser();

        String defaultPrefix = ctx.getConfig().prefix[0], guildPrefix = guildData.getGuildCustomPrefix();
        var prefix = guildPrefix == null ? defaultPrefix : guildPrefix;

        var languageContext = ctx.getLanguageContext();

        // Start building the help description.
        var description = new StringBuilder();
        if (category == null) {
            description.append(languageContext.get("commands.help.base"));
        } else {
            description.append(languageContext.get("commands.help.base_category")
                    .formatted(languageContext.get(category.toString()))
            );
        }

        description.append(languageContext.get("commands.help.support"));

        if (dbGuild.isPremium() || dbUser.isPremium()) {
            description.append(languageContext.get("commands.help.patreon"));
        }

        var disabledCommands = guildData.getDisabledCommands();
        if (!disabledCommands.isEmpty()) {
            description.append(languageContext.get("commands.help.disabled_commands").formatted(disabledCommands.size()));
        }

        var channelSpecificDisabledCommands = guildData.getChannelSpecificDisabledCommands();
        var disabledChannelCommands = channelSpecificDisabledCommands.get(ctx.getChannel().getId());
        if (disabledChannelCommands != null && !disabledChannelCommands.isEmpty()) {
            description.append("\n");
            description.append(
                    languageContext.get("commands.help.channel_specific_disabled_commands")
                            .formatted(disabledChannelCommands.size())
            );
        }
        // End of help description.

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(languageContext.get("commands.help.title"), null, ctx.getGuild().getIconUrl())
                .setColor(Color.PINK)
                .setDescription(description.toString())
                .setFooter(languageContext.get("commands.help.footer").formatted(
                        prefix,
                        CommandProcessor.REGISTRY.commands()
                                .values()
                                .stream()
                                .filter(c -> c.category() != null)
                                .count()
                ), ctx.getGuild().getIconUrl());

        Arrays.stream(CommandCategory.values())
                .filter(c -> {
                    if (category != null) {
                        return c == category;
                    } else {
                        return true;
                    }
                })
                .filter(c -> c != CommandCategory.OWNER || CommandPermission.OWNER.test(ctx.getMember()))
                .filter(c -> !CommandProcessor.REGISTRY.getCommandsForCategory(c).isEmpty())
                .forEach(c ->
                        embed.addField(
                                languageContext.get(c.toString()) + " " + languageContext.get("commands.help.commands") + ":",
                                forType(ctx.getChannel(), guildData, c), false
                        )
                );

        ctx.send(embed.build());
    }

    @Subscribe
    public void help(CommandRegistry cr) {
        final IncreasingRateLimiter rateLimiter = new IncreasingRateLimiter.Builder()
                .limit(1)
                .spamTolerance(2)
                .cooldown(2, TimeUnit.SECONDS)
                .maxCooldown(3, TimeUnit.SECONDS)
                .randomIncrement(true)
                .pool(MantaroData.getDefaultJedisPool())
                .prefix("help")
                .build();

        Random r = new Random();
        List<String> jokes = List.of(
                "Yo damn I heard you like help, because you just issued the help command to get the help about the help command.",
                "Congratulations, you managed to use the help command.",
                "Helps you to help yourself.",
                "Help Inception.",
                "A help helping helping helping help.",
                "I wonder if this is what you are looking for...",
                "Helping you help the world.",
                "The help you might need.",
                "Halp!"
        );

        cr.register("help", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                if (!RatelimitUtils.ratelimit(rateLimiter, ctx, false)) {
                    return;
                }

                var commandCategory = CommandCategory.lookupFromString(content);

                if (content.isEmpty()) {
                    buildHelp(ctx, null);
                } else if (commandCategory != null) {
                    buildHelp(ctx, commandCategory);
                } else {
                    var member = ctx.getMember();
                    var command = CommandProcessor.REGISTRY.commands().get(content);

                    if (command == null) {
                        ctx.sendLocalized("commands.help.extended.not_found", EmoteReference.ERROR);
                        return;
                    }

                    if (command.isOwnerCommand() && !CommandPermission.OWNER.test(member)) {
                        ctx.sendLocalized("commands.help.extended.not_found", EmoteReference.ERROR);
                        return;
                    }

                    var help = command.help();

                    if (help == null || help.getDescription() == null) {
                        ctx.sendLocalized("commands.help.extended.no_help", EmoteReference.ERROR);
                        return;
                    }

                    var descriptionList = help.getDescriptionList();
                    var languageContext = ctx.getLanguageContext();

                    var desc = new StringBuilder();
                    if (r.nextBoolean()) {
                        desc.append(languageContext.get("commands.help.patreon"))
                                .append("\n");
                    }

                    if (descriptionList.isEmpty()) {
                        desc.append(help.getDescription());
                    }
                    else {
                        desc.append(descriptionList.get(r.nextInt(descriptionList.size())));
                    }

                    desc.append("\n").append("**Don't include <> or [] on the command itself.**");

                    EmbedBuilder builder = new EmbedBuilder()
                            .setColor(Color.PINK)
                            .setAuthor("Command help for " + content, null,
                                    ctx.getAuthor().getEffectiveAvatarUrl()
                            ).setDescription(desc);

                    if (help.getUsage() != null) {
                        builder.addField("Usage", help.getUsage(), false);
                    }

                    if (help.getParameters().size() > 0) {
                        builder.addField("Parameters", help.getParameters().entrySet().stream()
                                .map(entry -> "`%s` - *%s*".formatted(entry.getKey(), entry.getValue()))
                                .collect(Collectors.joining("\n")),
                                false
                        );
                    }

                    if (help.isSeasonal()) {
                        builder.addField("Seasonal",
                                "This command allows the usage of the `-season` (or `-s`) argument.",
                                false
                        );
                    }

                    //Ensure sub-commands show in help.
                    //Only god shall help me now with all of this casting lol.
                    if (command instanceof AliasCommand) {
                        command = ((AliasCommand) command).getCommand();
                    }

                    if (command instanceof ITreeCommand) {
                        var subCommands =
                                ((ITreeCommand) command).getSubCommands()
                                        .entrySet()
                                        .stream()
                                        .sorted(Comparator.comparingInt(a ->
                                                a.getValue().description() == null ? 0 : a.getValue().description().length())
                                        ).collect(
                                                Collectors.toMap(
                                                        Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new
                                                )
                                );

                        var stringBuilder = new StringBuilder();

                        for (var inners : subCommands.entrySet()) {
                            var name = inners.getKey();
                            var inner = inners.getValue();
                            if (inner.isChild()) {
                                continue;
                            }

                            if (inner.description() != null) {
                                stringBuilder.append("""
                                        %s`%s` - %s
                                        """.formatted(BLUE_SMALL_MARKER, name, inner.description())
                                );
                            }
                        }

                        if (stringBuilder.length() > 0) {
                            builder.addField("Sub-commands",
                                    "**Append the main command to use any of this.**\n" + stringBuilder.toString(),
                                    false
                            );
                        }
                    }

                    //Known command aliases.
                    var commandAliases = command.getAliases();
                    if (!commandAliases.isEmpty()) {
                        String aliases = commandAliases
                                .stream()
                                .filter(alias -> !alias.equalsIgnoreCase(content))
                                .map("`%s`"::formatted)
                                .collect(Collectors.joining(" "));

                        if (!aliases.trim().isEmpty()) {
                            builder.addField("Aliases", aliases, false);
                        }
                    }

                    builder.addField("Still lost?",
                            "[Check the wiki](https://wiki.mantaro.site) or " +
                                    "[get support here!](https://support.mantaro.site)",  false
                    ).setFooter("Thanks for using Mantaro!", ctx.getAuthor().getEffectiveAvatarUrl());

                    ctx.send(builder.build());
                }
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("I wonder if this is what you are looking for...")
                        .setDescriptionList(jokes)
                        .setUsage("`~>help <command>`")
                        .addParameter("command", "The command name of the command you want to check information about.")
                        .build();
            }
        });

        cr.registerAlias("help", "commands");
        cr.registerAlias("help", "halp"); //why not
    }

    @Subscribe
    public void invite(CommandRegistry cr) {
        cr.register("invite", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                var languageContext = ctx.getLanguageContext();

                ctx.send(new EmbedBuilder()
                        .setAuthor("Mantaro's Invite URL.", null, ctx.getSelfUser().getAvatarUrl())
                        .addField(languageContext.get("commands.invite.url"),
                                "http://add.mantaro.site",
                                false
                        )
                        .addField(languageContext.get("commands.invite.server"),
                                "https://support.mantaro.site",
                                false)

                        .addField(languageContext.get("commands.invite.patreon"),
                                "http://patreon.com/mantaro",
                                false
                        )
                        .setDescription(
                                languageContext.get("commands.invite.description.1") + " " +
                                languageContext.get("commands.invite.description.2") + "\n" +
                                languageContext.get("commands.invite.description.3") + " " +
                                languageContext.get("commands.invite.description.4")
                        )
                        .setFooter(languageContext.get("commands.invite.footer"), ctx.getSelfUser().getAvatarUrl())
                        .build()
                );
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Gives you a bot OAuth invite link and some other important links.")
                        .build();
            }
        });
    }

    @Subscribe
    public void prefix(CommandRegistry cr) {
        cr.register("prefix", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                var dbGuild = ctx.getDBGuild();

                var defaultPrefix = Stream.of(ctx.getConfig().getPrefix())
                        .map(prefix -> "`" + prefix + "`")
                        .collect(Collectors.joining(" "));

                var guildPrefix = dbGuild.getData().getGuildCustomPrefix();

                ctx.sendLocalized("commands.prefix.header", EmoteReference.HEART,
                        defaultPrefix, guildPrefix == null ?
                                ctx.getLanguageContext().get("commands.prefix.none") : guildPrefix
                );
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Gives you information on how to change the prefix and what's the current prefix. " +
                                "If you looked at help, to change the prefix " +
                                "use `~>opts prefix set <prefix>`")
                        .build();
            }
        });
    }

    @Subscribe
    public void stats(CommandRegistry cr) {
        SimpleTreeCommand statsCommand = cr.register("stats", new SimpleTreeCommand(CommandCategory.INFO) {
            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("See the bot, usage or vps statistics.")
                        .setUsage("~>stats <option>` - Returns statistical information.")
                        .addParameter("option", "What to check for. See subcommands")
                        .build();
            }
        });

        statsCommand.addSubCommand("usage", new SubCommand() {
            @Override
            public String description() {
                return "The bot's (and JVM) hardware usage";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                ctx.send(new EmbedBuilder()
                                .setAuthor(languageContext.get("commands.stats.usage.header"), null, ctx.getSelfUser().getAvatarUrl())
                                .setDescription(languageContext.get("commands.stats.usage.description"))
                                .setThumbnail(ctx.getSelfUser().getAvatarUrl())
                                .addField(languageContext.get("commands.stats.usage.threads"),
                                        getThreadCount() + " Threads", false)
                                .addField(languageContext.get("commands.stats.usage.memory_usage"),
                                        Utils.formatMemoryUsage(getTotalMemory() - getFreeMemory(), getMaxMemory()), false)
                                .addField(languageContext.get("commands.stats.usage.cores"),
                                        getAvailableProcessors() + " Cores", true)
                                .addField(languageContext.get("commands.stats.usage.cpu_usage"),
                                        "%.2f%%".formatted(getInstanceCPUUsage() * 100), true)
                                .addField(languageContext.get("commands.stats.usage.assigned_mem"),
                                        Utils.formatMemoryAmount(getTotalMemory()), false)
                                .addField(languageContext.get("commands.stats.usage.assigned_remaining"),
                                        Utils.formatMemoryAmount(getFreeMemory()), true)
                                .build()
                );

                TextChannelGround.of(ctx.getEvent()).dropItemWithChance(4, 5);
            }
        });

        statsCommand.addSubCommand("nodes", new SubCommand() {
            @Override
            public String description() {
                return "Mantaro node statistics.";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                Map<String, String> nodeMap;
                try (Jedis jedis = ctx.getJedisPool().getResource()) {
                    nodeMap = jedis.hgetAll("node-stats-" + ctx.getConfig().getClientId());
                }

                var embed = new EmbedBuilder().setTitle("Mantaro Node Statistics")
                        .setDescription("This shows the current status of the online nodes. " +
                                "Every node contains a set amount of shards.")
                        .setThumbnail(ctx.getSelfUser().getAvatarUrl())
                        .setColor(Color.PINK)
                        .setFooter("Available Nodes: " + nodeMap.size());

                List<MessageEmbed.Field> fields = new LinkedList<>();
                nodeMap.entrySet().stream().sorted(
                        Comparator.comparingInt(e -> Integer.parseInt(e.getKey().split("-")[1]))
                ).forEach(node -> {
                    var nodeData = new JSONObject(node.getValue());
                    fields.add(new MessageEmbed.Field("Node " + node.getKey(),
                            """
                               **Uptime**: %s
                               **CPU Cores**: %s
                               **CPU Usage**: %s
                               **Memory**: %s
                               **Threads**: %,d
                               **Shards**: %s
                               **Guilds**: %,d
                               **User Cache**: %,d
                               **Machine Memory**: %s
                               """.formatted(
                                    Utils.formatDuration(nodeData.getLong("uptime")),
                                    nodeData.getLong("available_processors"),
                                    "%.2f%%".formatted(nodeData.getDouble("cpu_usage")),
                                    Utils.formatMemoryUsage(nodeData.getLong("used_memory"), nodeData.getLong("total_memory")),
                                    nodeData.getLong("thread_count"),
                                    nodeData.getString("shard_slice"),
                                    nodeData.getLong("guild_count"),
                                    nodeData.getLong("user_count"),
                                    Utils.formatMemoryAmount(nodeData.getLong("machine_total_memory"))
                            ), false
                    ));
                });

                DiscordUtils.sendPaginatedEmbed(ctx, embed, DiscordUtils.divideFields(3, fields));
            }
        });

        statsCommand.addSubCommand("lavalink", new SubCommand() {
            @Override
            public String description() {
                return "Lavalink node statistics.";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                List<LavalinkSocket> nodes = ctx.getBot().getLavaLink().getNodes();
                var embed = new EmbedBuilder();
                embed.setTitle("Lavalink Node Statistics")
                        .setDescription("This shows the current status of the online Lavalink nodes. " +
                                "Every node contains a dynamic amount of players. This is for balancing music processes " +
                                "outside of the main bot nodes.")
                        .setThumbnail(ctx.getSelfUser().getAvatarUrl())
                        .setColor(Color.PINK)
                        .setFooter("Available Nodes: " + nodes.size());

                List<MessageEmbed.Field> fields = new LinkedList<>();

                for (LavalinkSocket node : nodes) {
                    if (!node.isAvailable()) {
                        continue;
                    }

                    RemoteStats stats = node.getStats();
                    fields.add(new MessageEmbed.Field(node.getName(),
                            """
                            **Uptime:** %s
                            **Used Memory:** %s
                            **Free Memory:** %s
                            **Players:** %s
                            **Players Playing**: %,d
                            """.formatted(
                                    Utils.formatDuration(stats.getUptime()),
                                    Utils.formatMemoryAmount(stats.getMemUsed()),
                                    Utils.formatMemoryAmount(stats.getMemFree()),
                                    stats.getPlayers(),
                                    stats.getPlayingPlayers()
                            ), false
                    ));
                }

                DiscordUtils.sendPaginatedEmbed(ctx, embed, DiscordUtils.divideFields(3, fields));
            }
        });

        statsCommand.addSubCommand("cmds", new SubCommand() {
            @Override
            public String description() {
                return "The bot's command usage";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                String[] args = ctx.getArguments();
                if (args.length > 0) {
                    String what = args[0];
                    if (what.equals("total")) {
                        ctx.send(CommandStatsManager.fillEmbed(
                                DefaultBucket.TOTAL, baseEmbed(ctx, "Command Stats | Total")
                        ).build());

                        return;
                    }

                    if (what.equals("daily")) {
                        ctx.send(CommandStatsManager.fillEmbed(
                                DefaultBucket.DAY, baseEmbed(ctx, "Command Stats | Daily")
                        ).build());

                        return;
                    }

                    if (what.equals("hourly")) {
                        ctx.send(CommandStatsManager.fillEmbed(
                                DefaultBucket.HOUR, baseEmbed(ctx, "Command Stats | Hourly")
                        ).build());

                        return;
                    }

                    if (what.equals("now")) {
                        ctx.send(CommandStatsManager.fillEmbed(
                                DefaultBucket.MINUTE, baseEmbed(ctx, "Command Stats | Now")
                        ).build());

                        return;
                    }
                }

                ctx.send(
                        baseEmbed(ctx, "Command Stats")
                                .addField(languageContext.get("general.now"),
                                        CommandStatsManager.resume(DefaultBucket.MINUTE), false
                                )
                                .addField(languageContext.get("general.hourly"),
                                        CommandStatsManager.resume(DefaultBucket.HOUR), false
                                )
                                .addField(languageContext.get("general.daily"),
                                        CommandStatsManager.resume(DefaultBucket.DAY), false
                                )
                                .addField(languageContext.get("general.total"),
                                        CommandStatsManager.resume(DefaultBucket.TOTAL), false
                                ).build()
                );
            }
        });

        statsCommand.addSubCommand("category", new SubCommand() {
            @Override
            public String description() {
                return "The bot's category usage";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                String[] args = ctx.getArguments();
                if (args.length > 0) {
                    String what = args[0];

                    if (what.equals("total")) {
                        ctx.send(categoryStatsManager.fillEmbed(
                                CategoryStatsManager.TOTAL_CATS, baseEmbed(ctx, "Category Stats | Total")).build()
                        );

                        return;
                    }

                    if (what.equals("daily")) {
                        ctx.send(categoryStatsManager.fillEmbed(
                                CategoryStatsManager.DAY_CATS, baseEmbed(ctx, "Category Stats | Daily")
                        ).build());

                        return;
                    }

                    if (what.equals("hourly")) {
                        ctx.send(categoryStatsManager.fillEmbed(
                                CategoryStatsManager.HOUR_CATS, baseEmbed(ctx, "Category Stats | Hourly")
                        ).build());

                        return;
                    }

                    if (what.equals("now")) {
                        ctx.send(categoryStatsManager.fillEmbed(
                                CategoryStatsManager.MINUTE_CATS, baseEmbed(ctx, "Category Stats | Now")
                        ).build());

                        return;
                    }
                }

                ctx.send(
                        baseEmbed(ctx, "Category Stats")
                                .addField(languageContext.get("general.now"),
                                        categoryStatsManager.resume(CategoryStatsManager.MINUTE_CATS), false)
                                .addField(languageContext.get("general.hourly"),
                                        categoryStatsManager.resume(CategoryStatsManager.HOUR_CATS), false)
                                .addField(languageContext.get("general.daily"),
                                        categoryStatsManager.resume(CategoryStatsManager.DAY_CATS), false)
                                .addField(languageContext.get("general.total"),
                                        categoryStatsManager.resume(CategoryStatsManager.TOTAL_CATS), false)
                                .build()
                );
            }
        });
    }

    @Subscribe
    public void userinfo(CommandRegistry cr) {
        cr.register("userinfo", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                ctx.findMember(content, ctx.getMessage()).onSuccess(members -> {
                    var member = CustomFinderUtil.findMemberDefault(content, members, ctx, ctx.getMember());
                    if (member == null)
                        return;

                    var guildData = ctx.getDBGuild().getData();
                    var user = member.getUser();

                    var roles = member.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.joining(", "));

                    var languageContext = ctx.getLanguageContext();
                    var str = """
                            %1$s **%2$s:** %3$s
                            %1$s **%4$s:** %5$s
                            %1$s **%6$s:** %7$s
                            %1$s **%8$s:** %9$s
                            %1$s **%10$s:** %11$s
                            """.formatted(BLUE_SMALL_MARKER,
                            languageContext.get("commands.userinfo.id"), user.getId(),
                            languageContext.get("commands.userinfo.join_date"),
                            Utils.formatDate(member.getTimeJoined(), guildData.getLang()),
                            languageContext.get("commands.userinfo.created"),
                            Utils.formatDate(user.getTimeCreated(), guildData.getLang()),
                            languageContext.get("commands.userinfo.account_age"),
                            TimeUnit.MILLISECONDS.toDays(
                                    System.currentTimeMillis() - user.getTimeCreated().toInstant().toEpochMilli())
                                    + " " + languageContext.get("general.days"),
                            languageContext.get("commands.userinfo.vc"),
                            member.getVoiceState().getChannel() != null ?
                                    member.getVoiceState().getChannel().getName() : languageContext.get("general.none"),
                            languageContext.get("commands.userinfo.color"),
                            member.getColor() == null ? languageContext.get("commands.userinfo.default") :
                                    "#%s".formatted(Integer.toHexString(member.getColor().getRGB()).substring(2).toUpperCase())
                    );

                    ctx.send(new EmbedBuilder()
                            .setColor(member.getColor())
                            .setAuthor(
                                    languageContext.get("commands.userinfo.header")
                                            .formatted( user.getName(), user.getDiscriminator()),
                                    null, ctx.getAuthor().getEffectiveAvatarUrl()
                            )
                            .setThumbnail(user.getEffectiveAvatarUrl())
                            .setDescription(str)
                            .addField(
                                    languageContext.get("commands.userinfo.roles").formatted(member.getRoles().size()),
                                    StringUtils.limit(roles, 900), true
                            ).build()
                    );
                });
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("See information about specific users.")
                        .setUsage("`~>userinfo <@user>` - Get information about an user.")
                        .addParameter("user", "The user you want to look for. " +
                                "Mentions, nickname and user#discriminator work.")
                        .build();
            }
        });
    }

    @Subscribe
    public void season(CommandRegistry registry) {
        registry.register("season", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                I18nContext languageContext = ctx.getLanguageContext();

                ctx.sendFormat(languageContext.get("commands.season.info") +
                                languageContext.get("commands.season.info_2"),
                        ctx.getConfig().getCurrentSeason().getDisplay(), ctx.db().getAmountSeasonalPlayers()
                );
            }


            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Shows information about this season and about what's a season.")
                        .build();
            }
        });
    }

    @Subscribe
    public void support(CommandRegistry registry) {
        registry.register("support", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                ctx.sendLocalized("commands.support.info", EmoteReference.POPPER);
            }
        });
    }

    @Subscribe
    public void roleinfo(CommandRegistry cr) {
        cr.register("roleinfo", new SimpleCommand(CommandCategory.INFO) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                var role = FinderUtils.findRole(ctx.getEvent(), content);
                if (role == null) {
                    return;
                }

                var lang = ctx.getLanguageContext();
                var str = """
                        %1$s **%2$s:** %3$s
                        %1$s **%4$s:** %5$s
                        %1$s **%6$s:** %7$s
                        %1$s **%8$s:** %9$s
                        %1$s **%10$s:** %11$s
                        %1$s **%12$s:** %13$s
                        """.formatted(BLUE_SMALL_MARKER,
                        lang.get("commands.roleinfo.id"),
                        role.getId(),
                        lang.get("commands.roleinfo.created"),
                        Utils.formatDate(role.getTimeCreated(), ctx.getDBGuild().getData().getLang()),
                        lang.get("commands.roleinfo.color"),
                        role.getColor() == null ?
                                lang.get("general.none") :
                                "#%s".formatted(Integer.toHexString(role.getColor().getRGB()).substring(2)),
                        lang.get("commands.roleinfo.members"),
                        String.valueOf(ctx.getGuild()
                                .getMembers()
                                .stream()
                                .filter(member -> member.getRoles().contains(role))
                                .count()
                        ),
                        lang.get("commands.roleinfo.position"), role.getPosition(),
                        lang.get("commands.roleinfo.hoisted"), role.isHoisted()
                );

                ctx.send(
                        new EmbedBuilder()
                                .setColor(ctx.getMember().getColor())
                                .setAuthor(lang.get("commands.roleinfo.header").formatted(role.getName()),
                                        null, ctx.getGuild().getIconUrl()
                                )
                                .setDescription(str)
                                .addField(lang.get("commands.roleinfo.permissions").formatted(role.getPermissions().size()),
                                        role.getPermissions().size() == 0 ? lang.get("general.none") :
                                                role.getPermissions()
                                                        .stream()
                                                        .map(Permission::getName)
                                                        .collect(Collectors.joining(", ")) + ".",
                                        false
                                ).build()
                );
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("See information about specific role.")
                        .setUsage("`~>roleinfo <role>` - Get information about a role.")
                        .addParameter("role",
                                "The role you want to look for. Mentions, id and name work.")
                        .build();
            }
        });
    }
}
