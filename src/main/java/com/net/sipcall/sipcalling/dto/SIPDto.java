package com.net.sipcall.sipcalling.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiModel
public class SIPDto {

    /**
     * REGISTER from to 名字一样,
     */
    @ApiModelProperty(value = "用户配置号码")
    private String name;
    @ApiModelProperty(value = "用户配置密码")
    private String pass;
    @ApiModelProperty(value = "用户配置ip+端口号（可以直接用域名）")
    private String ip;
    /**
     * 本地ip
     */
    private String localIp;

    @ApiModelProperty(value = "用户配置端口（若ip为直接使用域名则不需要）")
    private String port;

    /**
     * INVITE to的号码
     */
    @ApiModelProperty(value = "被叫号码")
    private String number;

    @ApiModelProperty(value = "用户身份证号")
    private String userCard;
}
