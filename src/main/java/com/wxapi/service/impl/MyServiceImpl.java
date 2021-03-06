package com.wxapi.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wxapi.process.HttpMethod;
import com.wxapi.process.MsgType;
import com.wxapi.process.MsgXmlUtil;
import com.wxapi.process.WxAccountContext;
import com.wxapi.process.WxApi;
import com.wxapi.process.WxServiceProcess;
import com.wxapi.service.MyService;
import com.wxapi.vo.MsgRequest;
import com.wxcms.domain.Account;
import com.wxcms.domain.AccountFans;
import com.wxcms.domain.AccountMenu;
import com.wxcms.domain.MsgBase;
import com.wxcms.domain.MsgNews;
import com.wxcms.domain.MsgText;
import com.wxcms.mapper.AccountDao;
import com.wxcms.mapper.AccountFansDao;
import com.wxcms.mapper.AccountMenuDao;
import com.wxcms.mapper.AccountMenuGroupDao;
import com.wxcms.mapper.MsgBaseDao;
import com.wxcms.mapper.MsgNewsDao;
/**
 * 业务消息处理
 * @author 微信 qicong88
 */

@Service
public class MyServiceImpl implements MyService{

	@Autowired
	private MsgBaseDao msgBaseDao;
	
	@Autowired
	private MsgNewsDao msgNewsDao;
	
	@Autowired
	private AccountMenuDao menuDao;
	
	@Autowired
	private AccountMenuGroupDao menuGroupDao;
	
	@Autowired
	private AccountFansDao fansDao;
	
	@Autowired
	private AccountDao accountDao;
	
	
	//处理消息
	public String processMsg(MsgRequest msgRequest,String appId,String appSecret){
		String msgtype = msgRequest.getMsgType();
		String respXml = null;
		if(msgtype.equals(MsgType.Text.toString())){//文本消息
			respXml = this.processTextMsg(msgRequest,appId,appSecret);
		}else if(msgtype.equals(MsgType.Image.toString())){//图片消息
			
		}else if(msgtype.equals(MsgType.Location.toString())){//地理位置消息
			
		}else if(msgtype.equals(MsgType.Event.toString())){//事件消息
			respXml = this.processEventMsg(msgRequest);
		}
		//为空，返回 subscribe 消息
		if(StringUtils.isEmpty(respXml)){
			MsgText text = msgBaseDao.getMsgTextByInputCode(MsgType.SUBSCRIBE.toString());
			if(text != null){
				respXml = MsgXmlUtil.textToXml(WxServiceProcess.getMsgResponseText(msgRequest, text));
			}else{//随机返回3条图文消息
				List<MsgNews> msgNews = msgNewsDao.getRandomMsg(3);
				return MsgXmlUtil.newsToXml(WxServiceProcess.getMsgResponseNews(msgRequest, msgNews));
			}
		}
		return respXml;
	}
	
	//处理文本消息
	private String processTextMsg(MsgRequest msgRequest,String appId,String appSecret){
		String content = msgRequest.getContent();
		if(!StringUtils.isEmpty(content)){//文本消息
			String tmpContent = content.trim();
			List<MsgNews> msgNews = msgNewsDao.getRandomMsgByContent(tmpContent,3);//返回3条图文消息
			if(!CollectionUtils.isEmpty(msgNews)){
				return MsgXmlUtil.newsToXml(WxServiceProcess.getMsgResponseNews(msgRequest, msgNews));
			}else{
			    msgNews = msgNewsDao.getRandomMsg(3);//返回3条图文消息
			    return MsgXmlUtil.newsToXml(WxServiceProcess.getMsgResponseNews(msgRequest, msgNews));
			}
		}
		return null;
	}
	
	//处理事件消息
	private String processEventMsg(MsgRequest msgRequest){
		String key = msgRequest.getEventKey();
		
		if(MsgType.SUBSCRIBE.toString().equals(msgRequest.getEvent())){//订阅消息
			MsgText text = msgBaseDao.getMsgTextBySubscribe();
			if(text != null){
				return MsgXmlUtil.textToXml(WxServiceProcess.getMsgResponseText(msgRequest, text));
			}
		}else if(MsgType.UNSUBSCRIBE.toString().equals(msgRequest.getEvent())){//取消订阅
			
		}else{//CLICK
			if(!StringUtils.isEmpty(key)){
				if(key.startsWith("_fix_")){//固定消息
					String baseIds = key.substring("_fix_".length());
					if(!StringUtils.isEmpty(baseIds)){
						String[] idArr = baseIds.split(",");
						if(idArr.length > 1){//多条图文消息
							List<MsgNews> msgNews = msgBaseDao.listMsgNewsByBaseId(idArr);
							if(msgNews != null && msgNews.size() > 0){
								return MsgXmlUtil.newsToXml(WxServiceProcess.getMsgResponseNews(msgRequest, msgNews));
							}
						}else{//图文消息，或者文本消息
							MsgBase msg = msgBaseDao.getById(baseIds);
							if(msg.getMsgtype().equals(MsgType.Text.toString())){
								MsgText text = msgBaseDao.getMsgTextByBaseId(baseIds);
								if(text != null){
									return MsgXmlUtil.textToXml(WxServiceProcess.getMsgResponseText(msgRequest, text));
								}
							}else{
								List<MsgNews> msgNews = msgBaseDao.listMsgNewsByBaseId(idArr);
								if(msgNews != null && msgNews.size() > 0){
									return MsgXmlUtil.newsToXml(WxServiceProcess.getMsgResponseNews(msgRequest, msgNews));
								}
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	//发布菜单
	public JSONObject publishMenu(String gid,String appId, String appSecret){
		List<AccountMenu> menus = menuDao.listWxMenus(gid);
		JSONObject rstObj = WxServiceProcess.publishMenus(prepareMenus(menus), appId, appSecret);
		if(rstObj != null && rstObj.getInt("errcode") == 0){//成功，更新菜单组
			menuGroupDao.updateMenuGroupDisable();
			menuGroupDao.updateMenuGroupEnable(gid);
		}
		return rstObj;
	}
	
	//删除菜单
	public JSONObject deleteMenu(String appId, String appSecret){
		JSONObject rstObj = WxServiceProcess.deleteMenu(appId,appSecret);
		if(rstObj != null && rstObj.getInt("errcode") == 0){//成功，更新菜单组
			menuGroupDao.updateMenuGroupDisable();
		}
		return rstObj;
	}

	//获取用户列表
	public boolean syncAccountFansList(String appId,String appSecret){
		String nextOpenId = "";
		AccountFans lastFans = fansDao.getLastOpenId();
		if(lastFans != null){
			nextOpenId = lastFans.getOpenId();
		}
		return doSyncAccountFansList(nextOpenId,appId,appSecret);
	}
	
	//同步粉丝列表
	private boolean doSyncAccountFansList(String nextOpenId, String appId, String appSecret){
		if(!StringUtils.isEmpty(nextOpenId)){
			String url = WxApi.getFansListUrl(WxApi.getToken(appId,appSecret).getAccessToken(), nextOpenId);
			JSONObject jsonObject = WxApi.httpsRequest(url, HttpMethod.POST, null);
			if(!StringUtils.isEmpty(jsonObject.getString("errcode"))){
				return false;
			}
			List<AccountFans> fansList = new ArrayList<AccountFans>();
			JSONArray openidArr = jsonObject.getJSONObject("data").getJSONArray("openid");
			for(Object openId : openidArr){
				AccountFans fans = WxServiceProcess.syncAccountFans(openId.toString(), appId, appSecret);
				fansList.add(fans);
			}
			//批处理
			fansDao.addList(fansList);
			nextOpenId = jsonObject.getString("next_openid");
			doSyncAccountFansList(nextOpenId,appId,appSecret);
		}
		return true;
	}
	
	//获取用户信息接口 - 必须是开通了认证服务，否则微信平台没有开放此功能
	public AccountFans syncAccountFans(String openId, String appId, String appSecret ,boolean merge){
		AccountFans fans = WxServiceProcess.syncAccountFans(openId, appId, appSecret);
		if (merge && null != fans) {
			AccountFans tmpFans = fansDao.getByOpenId(openId);
			if(tmpFans == null){
				fansDao.add(fans);
			}else{
				fans.setId(tmpFans.getId());
				fansDao.add(fans);
			}
		}
		return fans;
	}
	
	//上传图文消息
	public JSONObject uploadMsgNews(String[] newIds,String appId,String appSecret){
		List<MsgNews> msgNewsList = msgNewsDao.getMsgNewsByIds(newIds);
		return WxServiceProcess.uploadNews(msgNewsList, appId, appSecret);
	}
	
	//群发图文消息
	public JSONObject sendMsgNewsAll(String[] newIds,String appId,String appSecret){
		JSONObject rstObj = uploadMsgNews(newIds,appId,appSecret);
		if(rstObj.containsKey("media_id")){
			return WxServiceProcess.sendAll(rstObj.getString("media_id"), MsgType.MPNEWS.toString(), appId, appSecret);
		}else{
			return rstObj;
		}
	}

	
	
	
	//wxcms method
	
	//获取微信公众账号的菜单
	private String prepareMenus(List<AccountMenu> menus) {
		if(!CollectionUtils.isEmpty(menus)){
			List<AccountMenu> parentAM = new ArrayList<AccountMenu>();
			Map<Long,List<JSONObject>> subAm = new HashMap<Long,List<JSONObject>>();
			for(AccountMenu m : menus){
				if(m.getParentid() == 0L){//一级菜单
					parentAM.add(m);
				}else{//二级菜单
					if(subAm.get(m.getParentid()) == null){
						subAm.put(m.getParentid(), new ArrayList<JSONObject>());
					}
					List<JSONObject> tmpMenus = subAm.get(m.getParentid());
					tmpMenus.add(getMenuJSONObj(m));
					subAm.put(m.getParentid(), tmpMenus);
				}
			}
			JSONArray arr = new JSONArray();
			for(AccountMenu m : parentAM){
				if(subAm.get(m.getId()) != null){//有子菜单
					arr.add(getParentMenuJSONObj(m,subAm.get(m.getId())));
				}else{//没有子菜单
					arr.add(getMenuJSONObj(m));
				}
			}
			JSONObject root = new JSONObject();
			root.put("button", arr);
			return JSONObject.fromObject(root).toString();
		}
		return "error";
	}
	
	/**
	 * 此方法是构建菜单对象的；构建菜单时，对于  key 的值可以任意定义；
	 * 当用户点击菜单时，会把key传递回来；对已处理就可以了
	 * @param menu
	 * @return
	 */
	private JSONObject getMenuJSONObj(AccountMenu menu){
		JSONObject obj = new JSONObject();
		obj.put("name", menu.getName());
		obj.put("type", menu.getMtype());
		if("click".equals(menu.getMtype())){//事件菜单
			if("fix".equals(menu.getEventType())){//fix 消息
				obj.put("key", "_fix_" + menu.getMsgId());//以 _fix_ 开头
			}else{
				if(StringUtils.isEmpty(menu.getInputcode())){//如果inputcode 为空，默认设置为 subscribe，以免创建菜单失败
					obj.put("key", "subscribe");
				}else{
					obj.put("key", menu.getInputcode());
				}
			}
		}else{//链接菜单-view
			obj.put("url", menu.getUrl());
		}
		return obj;
	}
	
	private JSONObject getParentMenuJSONObj(AccountMenu menu,List<JSONObject> subMenu){
		JSONObject obj = new JSONObject();
		obj.put("name", menu.getName());
		obj.put("sub_button", subMenu);
		return obj;
	}
	
	//获取Account
	public Account getByAccount(String account){
		Account act = accountDao.getByAccount(account);
		WxAccountContext.addAccount(act);
		return act;
	}
	
	//获取唯一的Account
	public Account getSingleAccount(){
		Account act = WxAccountContext.getSingleAccount();
		if(act == null){
			act = accountDao.getSingleAccount();
			WxAccountContext.addAccount(act);
		}
		return act;
	}
	
	//根据openid 获取粉丝，如果没有，同步粉丝
	public AccountFans getFansByOpenId(String openId,String appId,String appSecret){
		AccountFans fans = fansDao.getByOpenId(openId);
		if(fans == null){//如果没有，添加
			fans = WxServiceProcess.syncAccountFans(openId, appId, appSecret);
			if (null != fans) {
				fansDao.add(fans);
			}
		}
		return fans;
	}
	
}


