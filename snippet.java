/**
     * Invoice Template Scenarios Sheet #19
     * Regular invoice, credit invoice and then regular invoice again.
     */
    @Test
    public void testInvoiceSummaryScenario19() {
        TestEnvironment environment = testBuilder.getTestEnvironment();
        try {
            testBuilder.given(envBuilder -> {
                final JbillingAPI api = envBuilder.getPrancingPonyApi();
                //Set preference to consider non-anniversary, intermediate credit invoices for previous balance.
                PreferenceWS prefVal = api.getPreference(PREFERENCE_SHOULD_INCLUDE_CREDIT_INVOICE);
                prefVal.setValue("1");
                api.updatePreference(prefVal);

                logger.debug("Scenario #19 - Credit Invoice");
                Date nextInvoiceDate = TestUtils.AsDate(2016, 10, 1);
                Date activeSince = TestUtils.AsDate(2016, 10, 1);

                //Data set-up to create item/plan and orders
                Date pricingDate = TestUtils.AsDate(2016, 2, 1);

                List<Integer> items = Arrays.asList(INBOUND_USAGE_PRODUCT_ID, CHAT_USAGE_PRODUCT_ID, ACTIVE_RESPONSE_USAGE_PRODUCT_ID);
                PlanItemWS planItemProd1WS = buildPlanItem(api, items.get(0), MONTHLY_ORDER_PERIOD, "0", "0.10", pricingDate);
                PlanItemWS planItemProd2WS = buildPlanItem(api, items.get(1), MONTHLY_ORDER_PERIOD, "0", "0.20", pricingDate);
                PlanItemWS planItemProd3WS = buildPlanItem(api, items.get(2), MONTHLY_ORDER_PERIOD, "0", "0.30", pricingDate);

                buildAndPersistUsagePool(envBuilder, api, SCENARIO_19_USAGE_POOL, "100", envBuilder.idForCode(testCat1), items);
                buildAndPersistFlatProduct(envBuilder, api, SCENARIO_19_PLAN_PRODUCT, false, envBuilder.idForCode(testCat1), "70.00", true);
                buildAndPersistPlan(envBuilder, api, SCENARIO_19_PLAN, "100 Min Plan - $70.00 / Month", MONTHLY_ORDER_PERIOD,
                        envBuilder.idForCode(SCENARIO_19_PLAN_PRODUCT), Collections.singletonList(envBuilder.idForCode(SCENARIO_19_USAGE_POOL)),
                        planItemProd1WS, planItemProd2WS, planItemProd3WS);

                AssetWS scenario16Asset = getAssetIdByProductId(api, TOLL_FREE_8XX_NUMBER_ASSET_PRODUCT_ID);
                Map<Integer, Integer> productAssetMap = new HashMap<>();
                productAssetMap.put(TOLL_FREE_8XX_NUMBER_ASSET_PRODUCT_ID, scenario16Asset.getId());

                Map<Integer, BigDecimal> productQuantityMap = new HashMap<>();
                productQuantityMap.put(TOLL_FREE_8XX_NUMBER_ASSET_PRODUCT_ID, BigDecimal.ONE);
                productQuantityMap.put(environment.idForCode(SCENARIO_19_PLAN_PRODUCT), BigDecimal.ONE);

                InvoiceSummaryScenarioBuilder scenario19 = new InvoiceSummaryScenarioBuilder(testBuilder);
                Integer dailyPeriodId = buildAndPersistOrderPeriod(envBuilder, api, "testDailyPeriod", 1, PeriodUnitDTO.DAY);

                //Set customer's NID as 01-Oct-2016
                scenario19.createUser(SCENARIO_19_USER, environment.idForCode(testAccount), nextInvoiceDate, dailyPeriodId, nextInvoiceDay)
                        //Creating monthly subscription order on 01-Oct-2016
                        .createOrder(SCENARIO_19_MONTHLY_ORDER, activeSince, null, MONTHLY_ORDER_PERIOD, prePaidOrderTypeId, ORDER_CHANGE_STATUS_APPLY_ID, false,
                                productQuantityMap, productAssetMap, false)
                        //Creating one time order for Set Up Fee on 01-Oct-2016
                        .createOrder(SCENARIO_19_ONE_TIME_ORDER, activeSince, null, ONE_TIME_ORDER_PERIOD, postPaidOrderTypeId, ORDER_CHANGE_STATUS_APPLY_ID, false,
                                Collections.singletonMap(environment.idForCode(setUpFeeProduct), BigDecimal.ONE), null, false);

                //Generate invoice and validate invoice summary data.
            }).validate((testEnv, envBuilder) -> {
                final JbillingAPI api = envBuilder.getPrancingPonyApi();
                //Generating invoice for 01-Oct-2016
                Date runDate = TestUtils.AsDate(2016, 10, 1);

                api.createInvoiceWithDate(envBuilder.idForCode(SCENARIO_19_USER),
                        runDate, null, null, true);

                InvoiceWS invoice = api.getLatestInvoice(envBuilder.idForCode(SCENARIO_19_USER));
                ItemizedAccountWS itemizedAccountWS = api.getItemizedAccountByInvoiceId(invoice.getId());
                logger.debug("Scenario #19 Invoice Summary of 1st Invoice: {}", itemizedAccountWS.getInvoiceSummary());

                new ItemizedAccountTester(itemizedAccountWS).addExpectedPaymentReceived(new BigDecimal("0.00"))
                        .addExpectedAdjustmentCharges(new BigDecimal("0.00"))
                        .addExpectedFeesCharges(new BigDecimal("49.00"))
                        .addExpectedLastInvoiceDate(null)
                        .addExpectedMonthlyCharges(new BigDecimal("74.99"))
                        .addExpectedNewCharges(new BigDecimal("123.99"))
                        .addExpectedTaxesCharges(new BigDecimal("0.00"))
                        .addExpectedTotalDue(new BigDecimal("123.99"))
                        .addExpectedUsageCharges(new BigDecimal("0.00"))
                        .addExpectedAmountOfLastStatement(new BigDecimal("0.00"))
                        .validate();
                //Make the payment for previous invoice
            }).validate((testEnv, envBuilder) -> {
                //Making payment on 02-Oct-2016 to pay due amount of 01-Oct-2016 invoice.
                Date paymentDate = TestUtils.AsDate(2016, 10, 2);
                InvoiceSummaryScenarioBuilder scenarioBuilder = new InvoiceSummaryScenarioBuilder(testBuilder);
                scenarioBuilder
                        .selectUserByName(SCENARIO_19_USER)
                        .makePayment("123.99", paymentDate, false);

                final JbillingAPI api = envBuilder.getPrancingPonyApi();
                InvoiceWS invoice = api.getLatestInvoice(envBuilder.idForCode(SCENARIO_19_USER));
                assertEquals("Invoice balance is incorrect, ",
                        new BigDecimal("0.00"), invoice.getBalanceAsDecimal().setScale(2, BigDecimal.ROUND_HALF_UP));
                //Create credit adjustment order and generate credit invoice (negative invoice)
            }).validate((testEnv, envBuilder) -> {
                final JbillingAPI api = envBuilder.getPrancingPonyApi();
                Date nextInvoiceDate = TestUtils.AsDate(2016, 10, 3);
                Date activeSince = TestUtils.AsDate(2016, 10, 3);
                Date lastInvoiceDate = TestUtils.AsDate(2016, 10, 1);
                Date billingRunDate = TestUtils.AsDate(2016, 10, 3);

                //Updating user NID to 3-Oct-2016
                UserWS user = api.getUserWS(envBuilder.idForCode(SCENARIO_19_USER));
                user.setNextInvoiceDate(nextInvoiceDate);
                api.updateUser(user);

                InvoiceSummaryScenarioBuilder scenarioBuilder = new InvoiceSummaryScenarioBuilder(testBuilder);
                scenarioBuilder
                        .selectUserByName(SCENARIO_19_USER)
                        //Creating credit adjustment order
                        .createOrderWithPrice(SCENARIO_19_CREDIT_ADJUST_ORDER, activeSince, null, ONE_TIME_ORDER_PERIOD, postPaidOrderTypeId,
                                ORDER_CHANGE_STATUS_APPLY_ID, false, Collections.singletonMap(environment.idForCode(adjustMentProduct), BigDecimal.ONE),
                                Collections.singletonMap(environment.idForCode(adjustMentProduct), new BigDecimal("-50.00")));
                //Generating invoice for credit adjustment order on 03-Oct-2016
                api.createInvoiceWithDate(envBuilder.idForCode(SCENARIO_19_USER),
                        billingRunDate, null, null, false);

                InvoiceWS invoice = api.getLatestInvoice(envBuilder.idForCode(SCENARIO_19_USER));
                ItemizedAccountWS itemizedAccountWS = api.getItemizedAccountByInvoiceId(invoice.getId());
                logger.debug("Scenario #19 Invoice Summary after Credit Invoice: {}", itemizedAccountWS.getInvoiceSummary());
                new ItemizedAccountTester(itemizedAccountWS).addExpectedPaymentReceived(new BigDecimal("0.00"))
                        .addExpectedAdjustmentCharges(new BigDecimal("50.00").negate())
                        .addExpectedFeesCharges(new BigDecimal("0.00"))
                        .addExpectedLastInvoiceDate(lastInvoiceDate)
                        .addExpectedMonthlyCharges(new BigDecimal("0.00"))
                        .addExpectedNewCharges(new BigDecimal("50.00").negate())
                        .addExpectedTaxesCharges(new BigDecimal("0.00"))
                        .addExpectedTotalDue(new BigDecimal("50.00").negate())
                        .addExpectedUsageCharges(new BigDecimal("0.00"))
                        .addExpectedAmountOfLastStatement(new BigDecimal("123.99"))
                        .addExpectedPaymentReceived(new BigDecimal("123.99").negate())
                        .validate();

                //Generate regular invoice for next month post credit invoice
            }).validate((testEnv, envBuilder) -> {
                final JbillingAPI api = envBuilder.getPrancingPonyApi();
                //Updating user NID to 01-Nov-2016
                Date nextInvoiceDate = TestUtils.AsDate(2016, 11, 1);

                UserWS user = api.getUserWS(envBuilder.idForCode(SCENARIO_19_USER));
                user.setNextInvoiceDate(nextInvoiceDate);
                api.updateUser(user);

                //Generating invoice for 01-Nov-2016
                Date lastInvoiceDate = TestUtils.AsDate(2016, 10, 3);
                Date runDate = TestUtils.AsDate(2016, 11, 1);
                api.createInvoiceWithDate(envBuilder.idForCode(SCENARIO_19_USER),
                        runDate, null, null, false);

                InvoiceWS invoice = api.getLatestInvoice(envBuilder.idForCode(SCENARIO_19_USER));
                ItemizedAccountWS itemizedAccountWS = api.getItemizedAccountByInvoiceId(invoice.getId());
                logger.debug("Scenario #19 Regular Invoice Summary after credit adjustment: {}", itemizedAccountWS.getInvoiceSummary());

                new ItemizedAccountTester(itemizedAccountWS).addExpectedPaymentReceived(new BigDecimal("0.00"))
                        .addExpectedAdjustmentCharges(new BigDecimal("0.00"))
                        .addExpectedFeesCharges(new BigDecimal("0.00"))
                        .addExpectedLastInvoiceDate(lastInvoiceDate)
                        .addExpectedMonthlyCharges(new BigDecimal("74.99"))
                        .addExpectedNewCharges(new BigDecimal("74.99"))
                        .addExpectedTaxesCharges(new BigDecimal("0.00"))
                        .addExpectedTotalDue(new BigDecimal("24.99"))
                        .addExpectedUsageCharges(new BigDecimal("0.00"))
                        .addExpectedAmountOfLastStatement(new BigDecimal("50.00").negate())
                        .validate();

            });
            logger.debug("Invoice template test scenario #19 has been passed successfully");
        } finally {
            final JbillingAPI api = testBuilder.getTestEnvironment().getPrancingPonyApi();
            Arrays.stream(api.getUserInvoicesPage(testBuilder.getTestEnvironment().idForCode(SCENARIO_19_USER), 10, 0))
                    .forEach(invoice -> {
                        api.deleteInvoice(invoice.getId());
                    });
            PreferenceWS prefVal = api.getPreference(PREFERENCE_SHOULD_INCLUDE_CREDIT_INVOICE);
            prefVal.setValue("0");
            api.updatePreference(prefVal);
        }
    }

    ====================================================

    public Integer buildAndPersistOrderPeriod(TestEnvironmentBuilder envBuilder, JbillingAPI api,
    String description, Integer value, Integer unitId) {

return envBuilder.orderPeriodBuilder(api)
.withDescription(description)
.withValue(value)
.withUnitId(unitId)
.build();
}