import java.util.logging.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.databind.*;

/**
 * 远程管理 机器人。
 * 该机器人基本上就是：将控制台里的命令，用 Bot 再重新实现一遍，以达到：“不在电脑跟前时，用微信文字消息对 Bot 进行部分（嗯，只能是一部分）远程管理”的目的。
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_Manager extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnTextMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, boolean isContentMentionedMe, boolean isContentMentionedMeFirst
		)
	{
		try
		{
			if (StringUtils.equalsIgnoreCase (sContent, net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.manager.command.list-bots")))
			{
				StringBuilder sb = new StringBuilder ();
				for (int i=0; i<engine.listBots.size (); i++)
				{
					net_maclife_wechat_http_Bot bot = engine.listBots.get (i);
					sb.append (bot.GetName ());
					sb.append (" (");
					sb.append (bot.getClass ().getCanonicalName ());
					sb.append (")\n");
				}
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sb.toString ());
			}
			else if (isFromMe)
			{
				String[] arrayMessages = sContent.split ("\\s+", 2);
				if (arrayMessages==null || arrayMessages.length<1)
					return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

				String sCommandInputed = arrayMessages[0];
				String sCommandParametersInputed = null;
				if (arrayMessages.length >= 2)
					sCommandParametersInputed = arrayMessages[1];

				String[] arrayCommandOptions = sCommandInputed.split ("\\.+", 2);
				sCommandInputed = arrayCommandOptions[0];
				String sCommandOptionsInputed = null;
				if (arrayCommandOptions.length >= 2)
					sCommandOptionsInputed = arrayCommandOptions[1];

				if (StringUtils.equalsIgnoreCase (sCommandInputed, net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.manager.command.load-bot")))
				{
					LoadBot (sCommandParametersInputed, sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember);
				}
				else if (StringUtils.equalsIgnoreCase (sCommandInputed, net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.manager.command.unload-bot")))
				{
					boolean bForced = StringUtils.equalsIgnoreCase (sCommandOptionsInputed, "force");
					if (StringUtils.equals (sCommandParametersInputed, getClass ().getCanonicalName ()) && !bForced)
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "要卸载的机器人是本机器人，默认不允许这么操作，除非用 .force 选项强制执行");
						return 0;
					}
					UnloadBot (sCommandParametersInputed, sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember);
				}
				else if (StringUtils.equalsIgnoreCase (sCommandInputed, net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.manager.command.log-level")))
				{
					if (StringUtils.isEmpty (sCommandParametersInputed))
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "当前日志级别: " + net_maclife_wechat_http_BotApp.logger.getLevel ());
					}
					else
					{
						try
						{
							String sNewLogLevel = StringUtils.upperCase (sCommandParametersInputed);
							net_maclife_wechat_http_BotApp.logger.setLevel (Level.parse (sNewLogLevel));
							SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "日志级别已改为: " + net_maclife_wechat_http_BotApp.logger.getLevel ());
						}
						catch (IllegalArgumentException e)
						{
							e.printStackTrace ();
							SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "非法日志级别: " + sCommandParametersInputed + ", 请换有效的日志级别名称，比如 all finest finer fine info warning severe 1000 0 1 ...");
						}
					}
				}

				//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sContent);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	public void LoadBot (String sBotClassName, String sReplyToAccount, String sReplyToName, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember)
	{
		try
		{
			if (StringUtils.isEmpty (sBotClassName))
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "加载机器人需要指定机器人 java 类的完整类名");
				return;
			}
			Class<?> botClass = Class.forName (sBotClassName);
			Object obj = botClass.newInstance ();
			if (obj instanceof net_maclife_wechat_http_Bot)
			{
				net_maclife_wechat_http_Bot newBot = (net_maclife_wechat_http_Bot) obj;
				boolean bAlreadyLoaded = false;
				// 检查有没有该类的实例存在，有的话，则不再重复添加
				for (int i=0; i<engine.listBots.size (); i++)
				{
					net_maclife_wechat_http_Bot bot = engine.listBots.get (i);
					//if (bot.getClass ().isInstance (obj))
						if (botClass.isInstance (bot))
						{
							bAlreadyLoaded = true;
							SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "已经加载过 " + bot.GetName() + " 机器人，不重复加载");
							break;
						}
					}

					if (! bAlreadyLoaded)
					{
						newBot.SetEngine (engine);
						newBot.Start ();
						engine.listBots.add (newBot);
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, newBot.GetName () + " 机器人已成功创建并加载");
				}
			}
			//
		}
		catch (Exception e)
		{
			e.printStackTrace ();
			try
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "加载机器人 " + sBotClassName + " 时出现异常：" + e);
			}
			catch (Exception e1)
			{
				e1.printStackTrace();
			}
		}
	}

	public void UnloadBot (String sBotFullClassName, String sReplyToAccount, String sReplyToName, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember)
	{
		try
		{
			boolean bFound = false;
			for (int i=0; i<engine.listBots.size (); i++)
			{
				net_maclife_wechat_http_Bot bot = engine.listBots.get (i);
				if (StringUtils.equalsIgnoreCase (bot.getClass ().getCanonicalName (), sBotFullClassName))
				{
					bFound = true;
					//engine.UnloadBot (bot);
					engine.listBots.remove (bot);
					bot.Stop ();
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sBotFullClassName + " 机器人已被成功卸载");

					break;
				}
			}
			if (! bFound)
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "在已加载的机器人列表中找不到 " + sBotFullClassName + " 机器人");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
			try
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "卸载机器人 " + sBotFullClassName + " 时出现异常：" + e);
			}
			catch (Exception e1)
			{
				e1.printStackTrace();
			}
		}
	}
}
