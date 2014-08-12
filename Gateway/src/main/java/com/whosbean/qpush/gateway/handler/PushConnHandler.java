package com.whosbean.qpush.gateway.handler;

import com.whosbean.qpush.core.GsonUtils;
import com.whosbean.qpush.gateway.Commands;
import com.whosbean.qpush.gateway.Connection;
import com.whosbean.qpush.gateway.ServerMetrics;
import com.whosbean.qpush.gateway.keeper.ConnectionKeeper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Created by yaming_deng on 14-8-6.
 */
public class PushConnHandler extends ChannelInboundHandlerAdapter {

    protected static Logger logger = LoggerFactory.getLogger(PushConnHandler.class);

    private ThreadPoolTaskExecutor poolTaskExecutor;

    public PushConnHandler(){
        poolTaskExecutor = new ThreadPoolTaskExecutor();
        poolTaskExecutor.setCorePoolSize(10);
        poolTaskExecutor.setMaxPoolSize(100);
        poolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        poolTaskExecutor.afterPropertiesSet();
    }

    /**
     * 接收到新的连接
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        logger.info("channelActive: " + ctx.channel().hashCode());
    }

    /**
     * 读取新消息 LengthFieldBasedFrameDecoder 自动解包
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logger.info("channelRead: " + ctx.channel().hashCode());
        ByteBuf b = (ByteBuf)msg;
        byte[] dd = new byte[b.readableBytes()];
        b.readBytes(dd);
        String jsonString = new String(dd);
        logger.info(jsonString);

        ReferenceCountUtil.release(msg);
        ServerMetrics.incrMessageTotal();

        ClientPayload cc = GsonUtils.asT(ClientPayload.class, jsonString);
        if(cc.getCmd().intValue() == Commands.GO_ONLINE){
            ConnectionKeeper.add(cc.getAppKey(), cc.getUserId(), new Connection(ctx.channel()));
            poolTaskExecutor.submit(new OnNewlyAddThread(cc));
        }else if(cc.getCmd().intValue() == Commands.KEEP_ALIVE){
            //心跳

        }else if(cc.getCmd().intValue() == Commands.PUSH_ACK){
            //推送反馈

        }

        //回复客户端.
        final ByteBuf data = ctx.alloc().buffer(2); // (2)
        data.writeBytes("OK".getBytes());
        ChannelFuture cf = ctx.channel().writeAndFlush(data);
        if(cf.isDone() && cf.cause() != null){
            cf.cause().printStackTrace();
            ctx.close();
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("channelReadComplete: " + ctx.channel().hashCode());
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * 连接断开，移除连接影射，客户端发起重连
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx)
            throws Exception {
        logger.info("channelInactive: " + ctx.channel().hashCode());
        ConnectionKeeper.remove(ctx.channel().hashCode());
    }

}