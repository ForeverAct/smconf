package org.cxytiandi.conf.web.controller;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.cxytiandi.conf.client.common.Constant;
import org.cxytiandi.conf.client.common.EnvConstants;
import org.cxytiandi.conf.client.util.CommonUtil;
import org.cxytiandi.conf.web.common.LoginUserInfoUtils;
import org.cxytiandi.conf.web.domain.Conf;
import org.cxytiandi.conf.web.domain.UpdateLog;
import org.cxytiandi.conf.web.model.ConfModel;
import org.cxytiandi.conf.web.service.ConfService;
import org.cxytiandi.conf.web.service.UpdateLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.common.collect.Lists;

/**
 * 配置管理控制器
 * @author yinjihuan
 */
@Controller
public class ConfController {
	
	@Autowired
	private ConfService confService;
	
	@Autowired
	private UpdateLogService updateLogService;
	
	/**
	 * 配置列表页面
	 * @author yinjihuan
	 * @param conf
	 * @param model
	 * @return
	 */
	@GetMapping("/")
	public Object index(ConfModel conf, Map<String, Object> model, HttpServletRequest request) {
		List<String> envs = LoginUserInfoUtils.getLoginUserEvns(request);
		if (StringUtils.isBlank(conf.getEnv())) {
			if (envs.contains(EnvConstants.DEV)) {
				conf.setEnv(EnvConstants.DEV);
			} else if (envs.contains(EnvConstants.TEST)) {
				conf.setEnv(EnvConstants.TEST);
			} else if (envs.contains(EnvConstants.ONLINE)) {
				conf.setEnv(EnvConstants.ONLINE);
			} else if (envs.contains(EnvConstants.PROD)) {
				conf.setEnv(EnvConstants.PROD);
			}
		}
		
		if (envs.contains(conf.getEnv())) {
			if (conf.getPage() == 0) conf.setPage(1);
			List<Conf> list = confService.listForPage(conf.getEnv(), conf.getSystemName(), 
					conf.getConfFileName(), conf.getKey(), conf.getPage(), 20);
			List<ConfModel> results = Lists.newArrayList();
			for (Conf c : list) {
				ConfModel cm = new ConfModel();
				BeanUtils.copyProperties(c, cm);
				cm.setNodes(confService.getNodes(c.getEnv(), c.getSystemName(), c.getConfFileName()));
				results.add(cm);
			}
			model.put("confList", results);
			model.put("env", conf.getEnv());
			model.put("conf", conf);
			model.put("msg", "");
		} else {
			model.put("msg", "无操作权限");
			model.put("env", "");
			model.put("conf", new Conf());
		}
		
		return "conf/index";
	}
	
	/**
	 * 修改配置
	 * @author yinjihuan
	 * @param id
	 * @param value
	 * @param desc
	 * @return
	 */
	@PostMapping("/conf/update")
	@ResponseBody
	public Object update(String id, String value, String desc) {
		Object oldValue;
		Conf conf = confService.get(id);
		oldValue = conf.getValue();
		conf.setValue(value);
		conf.setModifyDate(new Date());
		confService.save(conf);
		
		//添加修改日志
		UpdateLog log = UpdateLog.builder().updateObjId(id)
				.updateTime(new Date())
				.oldValue(oldValue)
				.newValue(value)
				.username("yinjihuan")
				.updateDesc(desc).build();
		updateLogService.save(log);
		
		//修改zk中的节点的值，告诉客户端值有修改
		List<String> clients = CommonUtil.getZkClient().getClientServers(conf.getEnv(), conf.getSystemName());
		for (String client : clients) {
			if (client.split("&")[1].equals(conf.getConfFileName())) {
				CommonUtil.getZkClient().setValue(
						CommonUtil.buildPath(Constant.ZK_ROOT_PATH, conf.getEnv(), 
								conf.getSystemName(), client), value);
			}
		}
		
		return "success";
	}
	
	/**
	 * 删除配置
	 * @author yinjihuan
	 * @param id
	 * @return
	 */
	@PostMapping("/conf/remove")
	@ResponseBody
	public Object remove(String id) {
		confService.remove(id);
		return "success";
	}
}
