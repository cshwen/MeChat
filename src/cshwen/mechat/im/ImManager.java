/**
 * 
 */
package cshwen.mechat.im;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.PrivacyProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.GroupChatInvitation;
import org.jivesoftware.smackx.PrivateDataManager;
import org.jivesoftware.smackx.ReportedData;
import org.jivesoftware.smackx.ReportedData.Row;
import org.jivesoftware.smackx.bytestreams.socks5.provider.BytestreamsProvider;
import org.jivesoftware.smackx.packet.ChatStateExtension;
import org.jivesoftware.smackx.packet.LastActivity;
import org.jivesoftware.smackx.packet.OfflineMessageInfo;
import org.jivesoftware.smackx.packet.OfflineMessageRequest;
import org.jivesoftware.smackx.packet.SharedGroupsInfo;
import org.jivesoftware.smackx.provider.AdHocCommandDataProvider;
import org.jivesoftware.smackx.provider.DataFormProvider;
import org.jivesoftware.smackx.provider.DelayInformationProvider;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverItemsProvider;
import org.jivesoftware.smackx.provider.MUCAdminProvider;
import org.jivesoftware.smackx.provider.MUCOwnerProvider;
import org.jivesoftware.smackx.provider.MUCUserProvider;
import org.jivesoftware.smackx.provider.MessageEventProvider;
import org.jivesoftware.smackx.provider.MultipleAddressesProvider;
import org.jivesoftware.smackx.provider.RosterExchangeProvider;
import org.jivesoftware.smackx.provider.StreamInitiationProvider;
import org.jivesoftware.smackx.provider.VCardProvider;
import org.jivesoftware.smackx.provider.XHTMLExtensionProvider;
import org.jivesoftware.smackx.search.UserSearch;
import org.jivesoftware.smackx.search.UserSearchManager;

import cshwen.mechat.utils.Constants;
import cshwen.mechat.utils.FriendClass;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * @author CShWen
 * 
 */
public class ImManager {
	private static ImManager im = new ImManager();
	private static String TAG = ImManager.class.getSimpleName();
	private Handler handler;

	private static XMPPConnection connection;
	private static ConnectionConfiguration connConfig;
	private static Roster roster;
	/**
	 * 
	 */
	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	public static ImManager getInstance() {
		if (im == null)
			im = new ImManager();
		return im;
	}

	static {
		connConfig = new ConnectionConfiguration(Constants.MC_IP,
				Constants.MC_PORT);

		connection = new XMPPConnection(connConfig);

		ProviderManager pm = ProviderManager.getInstance();
		configure(pm);
		// connConfig.setTruststorePath("/system/etc/security/cacerts.bks");
		// connConfig.setTruststoreType("bks");
		roster=connection.getRoster();
		roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
	}

	public void loginUser(final String username, final String password) {
		new Thread() {
			@Override
			public void run() {
				/* 若连接未建立，则先建立连接 */
				if (!connection.isConnected()) {
					try {
						handler.sendEmptyMessage(Constants.CONNECTING);
						connection.connect();
					} catch (XMPPException e) {
						Log.e(TAG, e.toString());
						e.printStackTrace();
					}
				}

				/* 如果当前是未登录状态，则进行登录认证，若 已经登录，则直接进行操作 */
				if (!connection.isAuthenticated()) {
					handler.sendEmptyMessage(Constants.LOGINING);
					PacketFilter packetFilter = new PacketTypeFilter(IQ.class);
					PacketListener packetListener = new PacketListener() {
						@Override
						public void processPacket(Packet packet) {
							Log.e(TAG, packet.toXML());
							connection.removePacketListener(this);
							if (packet instanceof IQ) {
								IQ response = (IQ) packet;
								/* 登录失败，则提示失败信息 */
								if (response.getType() == IQ.Type.ERROR) {
									XMPPError error = response.getError();
									String errorCondition = error
											.getCondition();
									Log.e(TAG, errorCondition);
									Bundle bundle = new Bundle();
									bundle.putString(Constants.LOGIN_ERROR,
											errorCondition);
									Message msg = new Message();
									msg.setData(bundle);
									msg.what = Constants.LOGIN_FAILURE;
									handler.sendMessage(msg);
								} else if (response.getType() == IQ.Type.RESULT) {
									/* 登录成功，跳转到消息发送页面 */
									Log.e(TAG, "登录成功，跳转到消息发送页面");
									handler.sendEmptyMessage(Constants.LOGINED);
								}
							}
						}
					};
					try {
						connection.login(username, password);
					} catch (XMPPException e) {
						Log.e(TAG, e.toString());
						String error = e.getMessage();
						/* 如果是由于用户名未注册而登录失败，则重新注册该用户名 */
						if (error != null && error.contains("401")) {
							Bundle bundle = new Bundle();
							bundle.putString(Constants.LOGIN_ERROR, error);
							Message msg = new Message();
							msg.setData(bundle);
							msg.what = Constants.LOGIN_FAILURE;
							handler.sendMessage(msg);
						}
					}
					connection.addPacketListener(packetListener, packetFilter);
				} else {
					handler.sendEmptyMessage(Constants.LOGINED);
					Log.e(TAG, "已经处于登录状态，跳转到消息发送页面");
				}
			}
		}.start();
	}

	public void registerUser(final String un, final String pwd) {
		new Thread() {
			@Override
			public void run() {
				/* 若连接未建立，则先建立连接 */
				if (!connection.isConnected()) {
					try {
						handler.sendEmptyMessage(Constants.CONNECTING);
						connection.connect();
					} catch (XMPPException e) {
						Log.e(TAG, e.toString());
						e.printStackTrace();
					}
				}
				handler.sendEmptyMessage(Constants.REGISTING);
				Registration registration = new Registration();
				PacketFilter packetFilter = new AndFilter(new PacketIDFilter(
						registration.getPacketID()), new PacketTypeFilter(
						IQ.class));
				PacketListener packetListener = new PacketListener() {
					@Override
					public void processPacket(Packet packet) {
						Log.e(TAG, packet.toXML());
						connection.removePacketListener(this);
						if (packet instanceof IQ) {
							IQ response = (IQ) packet;
							/* 注册失败，则提示失败信息 */
							if (response.getType() == IQ.Type.ERROR) {
								XMPPError error = response.getError();
								String errorCondition = error.getCondition();
								Log.e(TAG, errorCondition);
								Bundle bundle = new Bundle();
								bundle.putString(Constants.REGIST_ERROR,
										errorCondition);
								Message msg = new Message();
								msg.setData(bundle);
								msg.what = Constants.REGIST_FAILURE;
								handler.sendMessage(msg);
							} else if (response.getType() == IQ.Type.RESULT) {
								handler.sendEmptyMessage(Constants.REGISTED);
							}
						}
					}
				};
				connection.addPacketListener(packetListener, packetFilter);
				registration.setType(IQ.Type.SET);
				registration.addAttribute("username", un);
				registration.addAttribute("password", pwd);
				connection.sendPacket(registration);
			}
		}.start();
	}

	public void exit() {
		if (connection.isConnected())
			connection.disconnect();
		connection = new XMPPConnection(connConfig);
		Log.i(TAG, "关闭连接");
	}

	public XMPPConnection getConnection() {
		if (!connection.isConnected()) {
			try {
				connection.connect();
			} catch (XMPPException e) {
				Log.e(TAG, e.toString());
				e.printStackTrace();
			}
		}
		return connection;
	}

	public ArrayList<FriendClass> searchUser(String searchName) {
		ArrayList<FriendClass> datas = new ArrayList<FriendClass>();
		try {
			UserSearchManager search = new UserSearchManager(getConnection());
			Form searchForm = search.getSearchForm("search."
					+ Constants.MC_SERVER);
			Form answerForm = searchForm.createAnswerForm();
			answerForm.setAnswer("Username", true);
			answerForm.setAnswer("search", searchName);
			ReportedData data = search.getSearchResults(answerForm, "search."
					+ Constants.MC_SERVER);

			Iterator<Row> it = data.getRows();
			Row row = null;
			while (it.hasNext()) {
				String ansS = "";
				row = it.next();
				System.out.println("[csw测试的]:"
						+ row.getValues("Username").next() + "|||"
						+ row.getValues("Name").next() + "|||"
						+ row.getValues("Email").next() + "|||"
						+ row.getValues("JID").next());
				datas.add(new FriendClass((String) row.getValues("JID").next(),
						(String) row.getValues("Username").next(), (String) row
								.getValues("Name").next(), (String) row
								.getValues("Email").next()));
			}
		} catch (Exception e) {
			System.out.println("[csw异常]:" + e.getClass().toString());
			e.printStackTrace();
		}
		return datas;
	}

	public void addFriend(String addJID, String nick) {
		try {
			// roster.createEntry(addName, null, new String[] { "friends" });
			roster.createEntry(addJID, nick, null);
		} catch (XMPPException e) {
			e.printStackTrace();
		}
	}

	public void delFriend(String delJID) {
		try {
			roster.removeEntry(roster.getEntry(delJID));
		} catch (XMPPException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<FriendClass> showFriends() {
		ArrayList<FriendClass> datas = new ArrayList<FriendClass>();
		Collection<RosterEntry> it = roster.getEntries();
		ArrayList<String> friends = new ArrayList<String>();
		for (RosterEntry rosterEnter : it) {
			friends.add(rosterEnter.getUser());
			// getUser为JID，getName为自定义昵称
			datas.add(new FriendClass(rosterEnter.getUser(), rosterEnter
					.getName(), rosterEnter.getName(), null));
		}
		if (friends.size() == 0) {
			friends.add("You have no friend");
		}
		return datas;
	}

	public void contactsListen(){
		roster.addRosterListener(new RosterListener() {
			public void presenceChanged(Presence presence) {
				String friendMood = presence.getStatus();
				System.out.println("监听好友状态改变消息是：" + presence.getStatus());
			}

			public void entriesUpdated(Collection<String> invites) {
				String uJID = null;
				for (Iterator iter = invites.iterator(); iter.hasNext();) {
					uJID= (String) iter.next();
				}
				System.out.println("CShWen同意添加的好友是：" + uJID);
				Message msg = Message.obtain();
				msg.obj = uJID;
				msg.what = Constants.FRIEND_AGREE;
				handler.sendMessage(msg);
			}

			public void entriesDeleted(Collection<String> invites) {
				System.out.println("CShWen监听到删除好友的消息是：" + invites);
				String uJID = null;
				for (Iterator iter = invites.iterator(); iter.hasNext();) {
					uJID = (String) iter.next();
				}
				Message msg = Message.obtain();
				msg.obj = uJID;
				msg.what = Constants.FRIEND_STOP;
				handler.sendMessage(msg);
			}

			public void entriesAdded(Collection<String> invites) {
				String uJID = null;
				for (Iterator iter = invites.iterator(); iter.hasNext();) {
					uJID = (String) iter.next();
				}
				System.out.println("CShWen申请添加好友的是：" + uJID);
				Message msg = Message.obtain();
				msg.obj = uJID;
				msg.what = Constants.FRIEND_APPLY;
				handler.sendMessage(msg);
			}
		});
	}
	
	public void isPresence(String un) {
		System.out.println("[cshwen|un是否在线：]" + roster.getPresence(un));
	}
	
	public void setMsgFilter(PacketListener listener,PacketFilter filter){ // 添加接受消息监听器
		getConnection().addPacketListener(listener, filter);
	}

	public static void configure(ProviderManager pm) {

		// Private Data Storage
		pm.addIQProvider("query", "jabber:iq:private",
				new PrivateDataManager.PrivateDataIQProvider());

		// Time
		try {
			pm.addIQProvider("query", "jabber:iq:time",
					Class.forName("org.jivesoftware.smackx.packet.Time"));
		} catch (ClassNotFoundException e) {
			Log.w("TestClient",
					"Can't load class for org.jivesoftware.smackx.packet.Time");
		}

		// Roster Exchange
		pm.addExtensionProvider("x", "jabber:x:roster",
				new RosterExchangeProvider());

		// Message Events
		pm.addExtensionProvider("x", "jabber:x:event",
				new MessageEventProvider());

		// Chat State
		pm.addExtensionProvider("active",
				"http://jabber.org/protocol/chatstates",
				new ChatStateExtension.Provider());

		pm.addExtensionProvider("composing",
				"http://jabber.org/protocol/chatstates",
				new ChatStateExtension.Provider());

		pm.addExtensionProvider("paused",
				"http://jabber.org/protocol/chatstates",
				new ChatStateExtension.Provider());

		pm.addExtensionProvider("inactive",
				"http://jabber.org/protocol/chatstates",
				new ChatStateExtension.Provider());

		pm.addExtensionProvider("gone",
				"http://jabber.org/protocol/chatstates",
				new ChatStateExtension.Provider());

		// XHTML
		pm.addExtensionProvider("html", "http://jabber.org/protocol/xhtml-im",
				new XHTMLExtensionProvider());

		// Group Chat Invitations
		pm.addExtensionProvider("x", "jabber:x:conference",
				new GroupChatInvitation.Provider());

		// Service Discovery # Items
		pm.addIQProvider("query", "http://jabber.org/protocol/disco#items",
				new DiscoverItemsProvider());

		// Service Discovery # Info
		pm.addIQProvider("query", "http://jabber.org/protocol/disco#info",
				new DiscoverInfoProvider());

		// Data Forms
		pm.addExtensionProvider("x", "jabber:x:data", new DataFormProvider());

		// MUC User
		pm.addExtensionProvider("x", "http://jabber.org/protocol/muc#user",
				new MUCUserProvider());

		// MUC Admin
		pm.addIQProvider("query", "http://jabber.org/protocol/muc#admin",
				new MUCAdminProvider());

		// MUC Owner
		pm.addIQProvider("query", "http://jabber.org/protocol/muc#owner",
				new MUCOwnerProvider());

		// Delayed Delivery
		pm.addExtensionProvider("x", "jabber:x:delay",
				new DelayInformationProvider());

		// Version
		try {
			pm.addIQProvider("query", "jabber:iq:version",
					Class.forName("org.jivesoftware.smackx.packet.Version"));
		} catch (ClassNotFoundException e) {
			// Not sure what's happening here.
		}
		// VCard
		pm.addIQProvider("vCard", "vcard-temp", new VCardProvider());

		// Offline Message Requests
		pm.addIQProvider("offline", "http://jabber.org/protocol/offline",
				new OfflineMessageRequest.Provider());

		// Offline Message Indicator
		pm.addExtensionProvider("offline",
				"http://jabber.org/protocol/offline",
				new OfflineMessageInfo.Provider());

		// Last Activity
		pm.addIQProvider("query", "jabber:iq:last", new LastActivity.Provider());

		// User Search
		pm.addIQProvider("query", "jabber:iq:search", new UserSearch.Provider());

		// SharedGroupsInfo
		pm.addIQProvider("sharedgroup",
				"http://www.jivesoftware.org/protocol/sharedgroup",
				new SharedGroupsInfo.Provider());

		// JEP-33: Extended Stanza Addressing
		pm.addExtensionProvider("addresses",
				"http://jabber.org/protocol/address",
				new MultipleAddressesProvider());

		// FileTransfer
		pm.addIQProvider("si", "http://jabber.org/protocol/si",
				new StreamInitiationProvider());

		pm.addIQProvider("query", "http://jabber.org/protocol/bytestreams",
				new BytestreamsProvider());

		// pm.addIQProvider("open", "http://jabber.org/protocol/ibb",
		// new IBBProviders.Open());
		//
		// pm.addIQProvider("close", "http://jabber.org/protocol/ibb",
		// new IBBProviders.Close());
		//
		// pm.addExtensionProvider("data", "http://jabber.org/protocol/ibb",
		// new IBBProviders.Data());

		// Privacy
		pm.addIQProvider("query", "jabber:iq:privacy", new PrivacyProvider());

		pm.addIQProvider("command", "http://jabber.org/protocol/commands",
				new AdHocCommandDataProvider());
		pm.addExtensionProvider("malformed-action",
				"http://jabber.org/protocol/commands",
				new AdHocCommandDataProvider.MalformedActionError());
		pm.addExtensionProvider("bad-locale",
				"http://jabber.org/protocol/commands",
				new AdHocCommandDataProvider.BadLocaleError());
		pm.addExtensionProvider("bad-payload",
				"http://jabber.org/protocol/commands",
				new AdHocCommandDataProvider.BadPayloadError());
		pm.addExtensionProvider("bad-sessionid",
				"http://jabber.org/protocol/commands",
				new AdHocCommandDataProvider.BadSessionIDError());
		pm.addExtensionProvider("session-expired",
				"http://jabber.org/protocol/commands",
				new AdHocCommandDataProvider.SessionExpiredError());
	}

}
