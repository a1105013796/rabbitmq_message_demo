package ink.cwblog.producter.service.impl;

import com.alibaba.fastjson.JSON;
import ink.cwblog.producter.dao.MessageLogDAO;
import ink.cwblog.producter.model.BaseModel;
import ink.cwblog.producter.model.MessageLog;
import ink.cwblog.producter.model.Order;
import ink.cwblog.producter.service.OrderService;
import ink.cwblog.producter.util.ConstantUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 *
 *
 * @description: 业务逻辑
 * @author: ChenWei
 * @create: 2020/5/13 - 20:50
 **/
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

	@Resource(name = "templateReliable")
	RabbitTemplate rabbitTemplate;

	@Autowired
	MessageLogDAO messageLogDAO;

	/**
	 * 交换机
	 */
	final String exchange = "OrderExchange";
	/**
	 * 路由键
	 */
	final String routingKey ="order.mail";



	/**
	 * 推送订单
	 *
	 * @param order
	 * @return
	 */
	@Override
	public BaseModel sendOrder(Order order) {
		String uuid = ConstantUtils.getUUID();
		order.setOid(uuid);
		try{
			//消息入库
			MessageLog messageLog = new MessageLog()
					.setMessage(JSON.toJSONString(order))
					.setMessageId(uuid)
					.setCreateTime(new Date())
					.setExchange(exchange)
					.setRoutingKey(routingKey)
					.setStatus(0) //设置状态0：投递中
					.setRetryCount(0);
			int insertResult = messageLogDAO.insertMessageLog(messageLog);
			if(insertResult==0){
				log.error("消息入库失败");
				return  new BaseModel().setCode(500).setMessage("消息入库失败").setResult("fail");
			}
			//发送消息， 将订单ID作为消息唯一ID
			rabbitTemplate.convertAndSend(exchange,routingKey,JSON.toJSONString(order),new CorrelationData(uuid));
			order.setOrderInfo(null);
			return new BaseModel().setCode(200).setMessage("消息推送成功").setResult("success").setData(order);
		}catch (Exception e){
			log.error("消息推送失败：{}",e);
			return new BaseModel().setCode(500).setMessage("消息推送失败").setResult("fail");
		}
	}
}