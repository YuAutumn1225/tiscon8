package com.tiscon.controller;

import com.tiscon.dao.EstimateDao;
import com.tiscon.code.OptionalServiceType;
import com.tiscon.dto.UserOrderDto;
import com.tiscon.code.PackageType;
import com.tiscon.form.UserOrderForm;
import com.tiscon.service.EstimateService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 引越し見積もりのコントローラークラス。
 *
 * @author Oikawa Yumi
 */
@Controller
public class EstimateController {

    private final EstimateDao estimateDAO;

    private final EstimateService estimateService;

    /**
     * コンストラクタ
     *
     * @param estimateDAO EstimateDaoクラス
     * @param estimateService EstimateServiceクラス
     */
    public EstimateController(EstimateDao estimateDAO, EstimateService estimateService) {
        this.estimateDAO = estimateDAO;
        this.estimateService = estimateService;
    }

    @GetMapping("")
    String index(Model model) {
        return "top";
    }

    /**
     * 入力画面に遷移する。
     *
     * @param model 遷移先に連携するデータ
     * @return 遷移先
     */
    @GetMapping("input")
    String input(Model model) {
        if (!model.containsAttribute("userOrderForm")) {
            model.addAttribute("userOrderForm", new UserOrderForm());
        }

        model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
        return "input";
    }

    /**
     * TOP画面に戻る。
     *
     * @param model 遷移先に連携するデータ
     * @return 遷移先
     */
    @PostMapping(value = "submit", params = "backToTop")
    String backToTop(Model model) {
        return "top";
    }

    /**
     * 確認画面に遷移する。
     *
     * @param userOrderForm 顧客が入力した見積もり依頼情報
     * @param model         遷移先に連携するデータ
     * @return 遷移先
     */
    @PostMapping(value = "submit", params = "confirm")
    String confirm(@Validated UserOrderForm userOrderForm, BindingResult result, Model model) {
        if (result.hasErrors()) {

            model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
            model.addAttribute("userOrderForm", userOrderForm);
            return "input";
        }

        model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
        model.addAttribute("userOrderForm", userOrderForm);
        return "confirm";
    }

    /**
     * 入力画面に戻る。
     *
     * @param userOrderForm 顧客が入力した見積もり依頼情報
     * @param model         遷移先に連携するデータ
     * @return 遷移先
     */
    @PostMapping(value = "result", params = "backToInput")
    String backToInput(UserOrderForm userOrderForm, Model model) {
        model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
        model.addAttribute("userOrderForm", userOrderForm);
        return "input";
    }

    /**
     * 確認画面に戻る。
     *
     * @param userOrderForm 顧客が入力した見積もり依頼情報
     * @param model         遷移先に連携するデータ
     * @return 遷移先
     */
    @PostMapping(value = "order", params = "backToInput")
    String backToConfirm(UserOrderForm userOrderForm, Model model) {
        model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
        model.addAttribute("userOrderForm", userOrderForm);
        return "input";
    }

    /**
     * 概算見積もり画面に遷移する。
     *
     * @param userOrderForm 顧客が入力した見積もり依頼情報
     * @param result        精査結果
     * @param model         遷移先に連携するデータ
     * @return 遷移先
     */
    @PostMapping(value = "result", params = "calculation")
    String calculation(@Validated UserOrderForm userOrderForm, BindingResult result, Model model) {
        if (result.hasErrors()) {

            model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
            model.addAttribute("userOrderForm", userOrderForm);
            return "confirm";
        }
        
        final int PRICE_PER_DISTANCE = 100;

        // 料金の計算を行う。
        UserOrderDto dto = new UserOrderDto();
        BeanUtils.copyProperties(userOrderForm, dto);
        Integer price = estimateService.getPrice(dto);
        
        String month_id = dto.getMovingMonth(); // month_id の取得

        String[] ids ={"0", "1", "2"}; // idのリスト
        double month_rate = 1.0; // rateの初期値
        String month = "";

        // 引っ越し予定期間によって引っ越し料の倍率、期間を変更する
        if (month_id.equals(ids[0])){
            month_rate = 1.5;
            month = "3月-4月";
        }else if (month_id.equals(ids[1])){
            month_rate = 1.2;
            month = "9月";
        }else if (month_id.equals(ids[2])){
            month_rate = 1.0;
            month = "5月-8月、10月-2月";
        }

        Integer bed_num = dto.getBed();
        Integer bicycle_num = dto.getBicycle();
        Integer box_num = dto.getBox();

        String old_prefecture = dto.getNewAddress();
        String new_prefecture = dto.getOldAddress();

        double distance = estimateDAO.getDistance(dto.getOldPrefectureId(), dto.getNewPrefectureId());

        Integer boxes = getBoxForPackage(dto.getBox(), PackageType.BOX)
                + getBoxForPackage(dto.getBed(), PackageType.BED)
                + getBoxForPackage(dto.getBicycle(), PackageType.BICYCLE)
                + getBoxForPackage(dto.getWashingMachine(), PackageType.WASHING_MACHINE);
        
        // トラック輸送量
        int track_price = 0;
        if (boxes <= 80){
            track_price = 30000;
        }else if (80 < boxes){
            track_price  = 50000;
        }

        // 小数点以下を切り捨てる
        Integer distanceInt = (int) Math.floor(distance);

        // 距離当たりの料金を算出する
        int priceForDistance = distanceInt * PRICE_PER_DISTANCE;

        // 箱に応じてトラックの種類が変わり、それに応じて料金が変わるためトラック料金を算出する。
        int pricePerTruck = estimateDAO.getPricePerTruck(boxes);

        // オプションサービスの料金を算出する。
        int priceForOptionalService = 0;

        boolean washingMachineInstall = dto.getWashingMachineInstallation();

        if (dto.getWashingMachineInstallation()) {
            priceForOptionalService = estimateDAO.getPricePerOptionalService(OptionalServiceType.WASHING_MACHINE.getCode());
        }

        double tracking_num = (double)(priceForDistance + pricePerTruck); // 重み付け
        int weighted_tracking_num = (int) Math.floor(month_rate * tracking_num); // int に変更


        model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
        model.addAttribute("userOrderForm", userOrderForm);
        model.addAttribute("price", price);
        model.addAttribute("old_prefecture", old_prefecture);
        model.addAttribute("new_prefecture", new_prefecture);
        model.addAttribute("distance", distanceInt);
        model.addAttribute("boxes", boxes);
        model.addAttribute("bed_num", bed_num);
        model.addAttribute("bicycle_num", bicycle_num);
        model.addAttribute("box_num", box_num);
        model.addAttribute("option_price", priceForOptionalService);
        model.addAttribute("track_price", pricePerTruck);
        model.addAttribute("distance_price", priceForDistance);
        model.addAttribute("WashInstall", washingMachineInstall);
        model.addAttribute("track_price", track_price);
        model.addAttribute("month_rate", month_rate);
        model.addAttribute("month", month);
        model.addAttribute("trans_num", weighted_tracking_num);

        return "result";
    }

    /**
     * 申し込み完了画面に遷移する。
     *
     * @param userOrderForm 顧客が入力した見積もり依頼情報
     * @param result        精査結果
     * @param model         遷移先に連携するデータ
     * @return 遷移先
     */
    @PostMapping(value = "order", params = "complete")
    String complete(@Validated UserOrderForm userOrderForm, BindingResult result, Model model) {
        if (result.hasErrors()) {

            model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
            model.addAttribute("userOrderForm", userOrderForm);
            return "confirm";
        }

        UserOrderDto dto = new UserOrderDto();
        BeanUtils.copyProperties(userOrderForm, dto);
        estimateService.registerOrder(dto);

        return "complete";
    }

    // /**
    //  * Tips画面に戻る。
    //  *
    //  * @param model 遷移先に連携するデータ
    //  * @return 遷移先
    //  */
    // @PostMapping(value = "submit", params = "backToTop")
    // String backToTop(Model model) {
    //     return "top";
    // }

        /**
     * 入力画面に遷移する。
     *
     * @param model 遷移先に連携するデータ
     * @return 遷移先
     */
    @GetMapping("tips")
    String tips(Model model) {
        // if (!model.containsAttribute("userOrderForm")) {
        //     model.addAttribute("userOrderForm", new UserOrderForm());
        // }

        // model.addAttribute("prefectures", estimateDAO.getAllPrefectures());
        return "tips";
    }

    private int getBoxForPackage(int packageNum, PackageType type) {
        return packageNum * estimateDAO.getBoxPerPackage(type.getCode());
    }
}


