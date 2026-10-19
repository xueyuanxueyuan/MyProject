SELECT TO_CHAR(SYSDATE, 'YYYY') || '年'
       || TO_CHAR(SYSDATE, 'MM') || '月'
       || TO_CHAR(SYSDATE, 'DD') || '日' AS bgrq
FROM dual
