package aicc.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * MyBatis SQL 로깅 인터셉터
 * 
 * 실행되는 모든 SQL 쿼리를 로그로 출력합니다.
 * - 실제 바인딩된 파라미터 값 포함
 * - 실행 시간 측정
 * - 가독성 좋은 포맷
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class MyBatisSqlLogger implements Interceptor {
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            // SQL 실행
            Object result = invocation.proceed();
            
            // 실행 시간 계산
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            // SQL 로그 출력
            logSql(invocation, executionTime);
            
            return result;
        } catch (Exception e) {
            // 에러 발생 시에도 SQL 로그 출력
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            logSql(invocation, executionTime);
            throw e;
        }
    }
    
    private void logSql(Invocation invocation, long executionTime) {
        try {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = null;
            if (invocation.getArgs().length > 1) {
                parameter = invocation.getArgs()[1];
            }
            
            String sqlId = mappedStatement.getId();
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            Configuration configuration = mappedStatement.getConfiguration();
            
            // 실제 실행될 SQL 생성
            String sql = getSql(configuration, boundSql);
            
            // 로그 출력
            log.info("\n" +
                    "===========================================================================================================\n" +
                    "[ {} ]\n" +
                    "[ Mapper Method ] : {}\n" +
                    "[ Execution Time ] : {}ms\n" +
                    "[ SQL ] :\n{}\n" +
                    "===========================================================================================================",
                    dateFormat.format(new Date()),
                    sqlId,
                    executionTime,
                    sql
            );
        } catch (Exception e) {
            log.error("SQL 로그 출력 중 오류 발생", e);
        }
    }
    
    /**
     * 실제 실행될 SQL 문자열 생성 (파라미터 바인딩 포함)
     */
    private String getSql(Configuration configuration, BoundSql boundSql) {
        String sql = boundSql.getSql();
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        
        // SQL 포맷팅 (공백 정리)
        sql = sql.replaceAll("[\\s]+", " ").trim();
        
        if (parameterMappings.size() > 0 && parameterObject != null) {
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                sql = sql.replaceFirst("\\?", getParameterValue(parameterObject));
            } else {
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                for (ParameterMapping parameterMapping : parameterMappings) {
                    String propertyName = parameterMapping.getProperty();
                    if (metaObject.hasGetter(propertyName)) {
                        Object obj = metaObject.getValue(propertyName);
                        sql = sql.replaceFirst("\\?", getParameterValue(obj));
                    } else if (boundSql.hasAdditionalParameter(propertyName)) {
                        Object obj = boundSql.getAdditionalParameter(propertyName);
                        sql = sql.replaceFirst("\\?", getParameterValue(obj));
                    }
                }
            }
        }
        
        return formatSql(sql);
    }
    
    /**
     * 파라미터 값을 SQL에 적합한 문자열로 변환
     */
    private String getParameterValue(Object obj) {
        if (obj == null) {
            return "NULL";
        }
        
        if (obj instanceof String) {
            return "'" + obj.toString().replace("'", "''") + "'";
        } else if (obj instanceof Date) {
            return "'" + dateFormat.format((Date) obj) + "'";
        } else if (obj instanceof java.time.LocalDateTime) {
            return "'" + obj.toString() + "'";
        } else if (obj instanceof java.time.LocalDate) {
            return "'" + obj.toString() + "'";
        } else if (obj instanceof java.time.LocalTime) {
            return "'" + obj.toString() + "'";
        }
        
        return obj.toString();
    }
    
    /**
     * SQL 포맷팅 (가독성 향상)
     */
    private String formatSql(String sql) {
        sql = sql.replaceAll("(?i)\\bSELECT\\b", "\nSELECT");
        sql = sql.replaceAll("(?i)\\bFROM\\b", "\nFROM");
        sql = sql.replaceAll("(?i)\\bWHERE\\b", "\nWHERE");
        sql = sql.replaceAll("(?i)\\bAND\\b", "\n  AND");
        sql = sql.replaceAll("(?i)\\bOR\\b", "\n  OR");
        sql = sql.replaceAll("(?i)\\bINSERT\\b", "\nINSERT");
        sql = sql.replaceAll("(?i)\\bINTO\\b", "\nINTO");
        sql = sql.replaceAll("(?i)\\bVALUES\\b", "\nVALUES");
        sql = sql.replaceAll("(?i)\\bUPDATE\\b", "\nUPDATE");
        sql = sql.replaceAll("(?i)\\bSET\\b", "\nSET");
        sql = sql.replaceAll("(?i)\\bDELETE\\b", "\nDELETE");
        sql = sql.replaceAll("(?i)\\bORDER BY\\b", "\nORDER BY");
        sql = sql.replaceAll("(?i)\\bGROUP BY\\b", "\nGROUP BY");
        sql = sql.replaceAll("(?i)\\bHAVING\\b", "\nHAVING");
        sql = sql.replaceAll("(?i)\\bLIMIT\\b", "\nLIMIT");
        sql = sql.replaceAll("(?i)\\bOFFSET\\b", "\nOFFSET");
        sql = sql.replaceAll("(?i)\\bJOIN\\b", "\nJOIN");
        sql = sql.replaceAll("(?i)\\bLEFT JOIN\\b", "\nLEFT JOIN");
        sql = sql.replaceAll("(?i)\\bRIGHT JOIN\\b", "\nRIGHT JOIN");
        sql = sql.replaceAll("(?i)\\bINNER JOIN\\b", "\nINNER JOIN");
        
        return sql.trim();
    }
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
    
    @Override
    public void setProperties(Properties properties) {
        // 추가 설정이 필요한 경우 사용
    }
}
