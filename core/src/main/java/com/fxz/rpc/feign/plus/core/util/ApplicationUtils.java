package com.fxz.rpc.feign.plus.core.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;


public class ApplicationUtils implements ApplicationContextAware {
    public static ApplicationContext applicationContext;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationUtils.applicationContext = applicationContext;
    }
}
