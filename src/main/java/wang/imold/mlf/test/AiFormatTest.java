package wang.imold.mlf.test;

import wang.imold.mlf.util.AiFormat;

public class AiFormatTest {
    public static void main(String[] args) throws Exception {
        AiFormat aiFormat = new AiFormat();
        String testStr = "==>  Preparing: SELECT id, patient_name, patient_id, gender, age, phone, department, bed_no, check_item, check_result, reference_range, unit, check_date, doctor, check_department, sample_type, diagnostic_note, status, create_time, update_time FROM t_labresult WHERE age >= ? AND check_date BETWEEN ? AND ? AND check_result LIKE ? AND sample_type = ? AND status IN (?,?) AND department = ? AND diagnostic_note IS NOT NULL ORDER BY check_date DESC LIMIT ?\n" +
                "==> Parameters: 50(Integer), 2025-01-01 00:00:00(Timestamp), 2025-12-31 23:59:59(Timestamp), %阳性%(String), 血液(String), 已审核(String), 已完成(String), 内分泌科(String), 20(Integer)\n" +
                "<==      Total: 18";
        String s = aiFormat.callDify(testStr);
        System.out.println(s);

    }
}
