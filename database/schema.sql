--
-- PostgreSQL database dump
--

\restrict xRbOqoW1VyVsYzhOdr6jCPXF0WA9TLxGDRSN9G0h5RAYKYvaSATGMBWIDg2J5dz

-- Dumped from database version 16.14 (Ubuntu 16.14-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.14 (Ubuntu 16.14-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.agency_follows DROP CONSTRAINT IF EXISTS "agency_follows_userId_fkey";
ALTER TABLE IF EXISTS ONLY public.agency_follows DROP CONSTRAINT IF EXISTS "agency_follows_agencyId_fkey";
ALTER TABLE IF EXISTS ONLY public.chat_participants DROP CONSTRAINT IF EXISTS "FK_ffa48c8c78e4c4d0cb29bd6d123";
ALTER TABLE IF EXISTS ONLY public.room_host_follows DROP CONSTRAINT IF EXISTS "FK_febd82d58a786b258d29c209523";
ALTER TABLE IF EXISTS ONLY public.follows DROP CONSTRAINT IF EXISTS "FK_fdb91868b03a2040db408a53331";
ALTER TABLE IF EXISTS ONLY public.chat_messages DROP CONSTRAINT IF EXISTS "FK_fc6b58e41e9a871dacbe9077def";
ALTER TABLE IF EXISTS ONLY public.chat_participants DROP CONSTRAINT IF EXISTS "FK_fb6add83b1a7acc94433d385692";
ALTER TABLE IF EXISTS ONLY public.room_seat_signals DROP CONSTRAINT IF EXISTS "FK_fa0749b418feb14a7f2757994ae";
ALTER TABLE IF EXISTS ONLY public.gift_sends DROP CONSTRAINT IF EXISTS "FK_f9f7aa11e20e27f32fc6456551e";
ALTER TABLE IF EXISTS ONLY public.agent_recharges DROP CONSTRAINT IF EXISTS "FK_f82f10254ad10ef1e6de447004b";
ALTER TABLE IF EXISTS ONLY public.agencies DROP CONSTRAINT IF EXISTS "FK_f1f8d72ccc44a6da7b7a2e8f773";
ALTER TABLE IF EXISTS ONLY public.lucky_box_opens DROP CONSTRAINT IF EXISTS "FK_f09cdd61f21d144c689b8acbdbf";
ALTER TABLE IF EXISTS ONLY public.follows DROP CONSTRAINT IF EXISTS "FK_ef463dd9a2ce0d673350e36e0fb";
ALTER TABLE IF EXISTS ONLY public.blocks DROP CONSTRAINT IF EXISTS "FK_ed8a33b2c1ac10922c2354f5cfc";
ALTER TABLE IF EXISTS ONLY public.room_seat_signals DROP CONSTRAINT IF EXISTS "FK_ea41e82e13c644166d9ea469fe4";
ALTER TABLE IF EXISTS ONLY public.agent_recharges DROP CONSTRAINT IF EXISTS "FK_e91dc58a06bb3ef1e4c530d905a";
ALTER TABLE IF EXISTS ONLY public.devices DROP CONSTRAINT IF EXISTS "FK_e8a5d59f0ac3040395f159507c6";
ALTER TABLE IF EXISTS ONLY public.female_identity_verifications DROP CONSTRAINT IF EXISTS "FK_df653e18001df19542aa4410141";
ALTER TABLE IF EXISTS ONLY public.rooms DROP CONSTRAINT IF EXISTS "FK_d7fa6127b9d1676d65c1785f7d7";
ALTER TABLE IF EXISTS ONLY public.room_seats DROP CONSTRAINT IF EXISTS "FK_ca59a2f22d45f00155364bbb6eb";
ALTER TABLE IF EXISTS ONLY public.room_bans DROP CONSTRAINT IF EXISTS "FK_c816c4cce8f709165187b6fb2e5";
ALTER TABLE IF EXISTS ONLY public.room_games DROP CONSTRAINT IF EXISTS "FK_bd3a636f5eb88d1273956ebcd98";
ALTER TABLE IF EXISTS ONLY public.drama_episodes DROP CONSTRAINT IF EXISTS "FK_bbd9b86138c398837390bb21671";
ALTER TABLE IF EXISTS ONLY public.blocks DROP CONSTRAINT IF EXISTS "FK_b87401da081a7153ec3bd1e6ab6";
ALTER TABLE IF EXISTS ONLY public.room_moderators DROP CONSTRAINT IF EXISTS "FK_b412281aebf17aeeff410e45790";
ALTER TABLE IF EXISTS ONLY public.withdraw_requests DROP CONSTRAINT IF EXISTS "FK_a8f03d80e42da1162e25591dc4e";
ALTER TABLE IF EXISTS ONLY public.agency_members DROP CONSTRAINT IF EXISTS "FK_a6bb192fadb7310808004f57535";
ALTER TABLE IF EXISTS ONLY public.user_cosmetics DROP CONSTRAINT IF EXISTS "FK_96c1bb2ccf043af338008825a77";
ALTER TABLE IF EXISTS ONLY public.agency_applications DROP CONSTRAINT IF EXISTS "FK_96b114455001b681fc42e21c24a";
ALTER TABLE IF EXISTS ONLY public.lucky_reward_grants DROP CONSTRAINT IF EXISTS "FK_9519ebc45afbce61f299349a408";
ALTER TABLE IF EXISTS ONLY public.room_games DROP CONSTRAINT IF EXISTS "FK_933c87654559418207c1c8b9612";
ALTER TABLE IF EXISTS ONLY public.lucky_box_opens DROP CONSTRAINT IF EXISTS "FK_8effac5e5a0f87cc71753d98e29";
ALTER TABLE IF EXISTS ONLY public.recharge_agent_ledger DROP CONSTRAINT IF EXISTS "FK_8b5199ab654843357766592c0d2";
ALTER TABLE IF EXISTS ONLY public.profile_visits DROP CONSTRAINT IF EXISTS "FK_8abdc3af0797198cc1edb6de942";
ALTER TABLE IF EXISTS ONLY public.recharge_agents DROP CONSTRAINT IF EXISTS "FK_891986e15af0e6c9936d3d25f92";
ALTER TABLE IF EXISTS ONLY public.user_profiles DROP CONSTRAINT IF EXISTS "FK_8481388d6325e752cd4d7e26c6d";
ALTER TABLE IF EXISTS ONLY public.room_seats DROP CONSTRAINT IF EXISTS "FK_8256f8fefaf42f4fdb204f58f40";
ALTER TABLE IF EXISTS ONLY public.user_cosmetics DROP CONSTRAINT IF EXISTS "FK_7ac096a8bfe8bad3810cf3b01b1";
ALTER TABLE IF EXISTS ONLY public.user_vips DROP CONSTRAINT IF EXISTS "FK_776c4d82b2a8bdf328bc3f51cc9";
ALTER TABLE IF EXISTS ONLY public.gift_sends DROP CONSTRAINT IF EXISTS "FK_70fe713ef5463e7aa66bbcb321d";
ALTER TABLE IF EXISTS ONLY public.room_moderators DROP CONSTRAINT IF EXISTS "FK_6e2c115bfdba758d450a5dc6846";
ALTER TABLE IF EXISTS ONLY public.rooms DROP CONSTRAINT IF EXISTS "FK_6c939085068c5539bad393be6b9";
ALTER TABLE IF EXISTS ONLY public.wallet_transactions DROP CONSTRAINT IF EXISTS "FK_69454773f1e666a14c6a9539353";
ALTER TABLE IF EXISTS ONLY public.notifications DROP CONSTRAINT IF EXISTS "FK_692a909ee0fa9383e7859f9b406";
ALTER TABLE IF EXISTS ONLY public.recharge_orders DROP CONSTRAINT IF EXISTS "FK_5f8501202b13cd3f35e2929e01a";
ALTER TABLE IF EXISTS ONLY public.room_bans DROP CONSTRAINT IF EXISTS "FK_4ddcc53b3247c634c352603dc0c";
ALTER TABLE IF EXISTS ONLY public.chat_messages DROP CONSTRAINT IF EXISTS "FK_45745953065384cc9c4264c2a3d";
ALTER TABLE IF EXISTS ONLY public.reports DROP CONSTRAINT IF EXISTS "FK_4353be8309ce86650def2f8572d";
ALTER TABLE IF EXISTS ONLY public.profile_visits DROP CONSTRAINT IF EXISTS "FK_41d42c9f76df6bacf81bdc16e5b";
ALTER TABLE IF EXISTS ONLY public.user_vips DROP CONSTRAINT IF EXISTS "FK_3f312d4a4d8632e8b31f58802d3";
ALTER TABLE IF EXISTS ONLY public.recharge_agent_applications DROP CONSTRAINT IF EXISTS "FK_33f0416f1aa1aa1976046d27ec0";
ALTER TABLE IF EXISTS ONLY public.social_requests DROP CONSTRAINT IF EXISTS "FK_32be8e6bd24d59149ebc4b29f27";
ALTER TABLE IF EXISTS ONLY public.wallets DROP CONSTRAINT IF EXISTS "FK_2ecdb33f23e9a6fc392025c0b97";
ALTER TABLE IF EXISTS ONLY public.social_requests DROP CONSTRAINT IF EXISTS "FK_291baf6ac9479a2645852d114a6";
ALTER TABLE IF EXISTS ONLY public.room_music_tracks DROP CONSTRAINT IF EXISTS "FK_22b88b48be38104518c775a1fbd";
ALTER TABLE IF EXISTS ONLY public.agency_applications DROP CONSTRAINT IF EXISTS "FK_1e41a88dbde6094d3c6ebe54441";
ALTER TABLE IF EXISTS ONLY public.room_games DROP CONSTRAINT IF EXISTS "FK_1bef6f4a4b8fa3b7abf26042b47";
ALTER TABLE IF EXISTS ONLY public.agency_members DROP CONSTRAINT IF EXISTS "FK_19ecab949371747d82ce365d6df";
ALTER TABLE IF EXISTS ONLY public.chat_messages DROP CONSTRAINT IF EXISTS "FK_17a42899ce72cd55dc25fb33139";
ALTER TABLE IF EXISTS ONLY public.room_host_follows DROP CONSTRAINT IF EXISTS "FK_14cf5cb9c3da401f4981574408e";
ALTER TABLE IF EXISTS ONLY public.gift_sends DROP CONSTRAINT IF EXISTS "FK_0e6521dead3cae78f5dfb996194";
DROP INDEX IF EXISTS public.uq_wallet_tx_user_reference;
DROP INDEX IF EXISTS public.uq_room_agency_host;
DROP INDEX IF EXISTS public.uq_agencies_name_ci;
DROP INDEX IF EXISTS public.idx_withdraw_requests_agent;
DROP INDEX IF EXISTS public.idx_users_public_id;
DROP INDEX IF EXISTS public."IDX_room_chat_penalties_room_user";
DROP INDEX IF EXISTS public."IDX_lucky_open_user_box_day";
DROP INDEX IF EXISTS public."IDX_gifts_brandAgencyId";
DROP INDEX IF EXISTS public."IDX_ffa48c8c78e4c4d0cb29bd6d12";
DROP INDEX IF EXISTS public."IDX_febd82d58a786b258d29c20952";
DROP INDEX IF EXISTS public."IDX_fe7931b7501576746f27f25d50";
DROP INDEX IF EXISTS public."IDX_fe0bb3f6520ee0469504521e71";
DROP INDEX IF EXISTS public."IDX_fdb91868b03a2040db408a5333";
DROP INDEX IF EXISTS public."IDX_fc6b58e41e9a871dacbe9077de";
DROP INDEX IF EXISTS public."IDX_fb6add83b1a7acc94433d38569";
DROP INDEX IF EXISTS public."IDX_fa0749b418feb14a7f2757994a";
DROP INDEX IF EXISTS public."IDX_f9f7aa11e20e27f32fc6456551";
DROP INDEX IF EXISTS public."IDX_f82f10254ad10ef1e6de447004";
DROP INDEX IF EXISTS public."IDX_f6fdeaefc5d27901a8ad3d7c48";
DROP INDEX IF EXISTS public."IDX_f1f8d72ccc44a6da7b7a2e8f77";
DROP INDEX IF EXISTS public."IDX_f09cdd61f21d144c689b8acbdb";
DROP INDEX IF EXISTS public."IDX_eff4bc5b2d97dc474531fb357a";
DROP INDEX IF EXISTS public."IDX_ef8e7397bbb9ce5bc09df5413d";
DROP INDEX IF EXISTS public."IDX_ef463dd9a2ce0d673350e36e0f";
DROP INDEX IF EXISTS public."IDX_ed8a33b2c1ac10922c2354f5cf";
DROP INDEX IF EXISTS public."IDX_ea41e82e13c644166d9ea469fe";
DROP INDEX IF EXISTS public."IDX_e9e5dd67910dbc65a3a6fbc155";
DROP INDEX IF EXISTS public."IDX_e91dc58a06bb3ef1e4c530d905";
DROP INDEX IF EXISTS public."IDX_e8a5d59f0ac3040395f159507c";
DROP INDEX IF EXISTS public."IDX_e761540486cbe026a6ac094208";
DROP INDEX IF EXISTS public."IDX_dfd1b05e9d1ad68adf710c1ec3";
DROP INDEX IF EXISTS public."IDX_df653e18001df19542aa441014";
DROP INDEX IF EXISTS public."IDX_ddd8591ce98b8751ed56795d9c";
DROP INDEX IF EXISTS public."IDX_dcd0c8a4b10af9c986e510b9ec";
DROP INDEX IF EXISTS public."IDX_d877012f96fd086d5a929911bc";
DROP INDEX IF EXISTS public."IDX_d7fa6127b9d1676d65c1785f7d";
DROP INDEX IF EXISTS public."IDX_d05e3de1eb08a867f84224ec64";
DROP INDEX IF EXISTS public."IDX_ceb65dda05e249f63954010ef5";
DROP INDEX IF EXISTS public."IDX_cd7faa31851a2153125fd1af73";
DROP INDEX IF EXISTS public."IDX_caed89ce0fe60c7c459c2a8332";
DROP INDEX IF EXISTS public."IDX_c816c4cce8f709165187b6fb2e";
DROP INDEX IF EXISTS public."IDX_c64f80e2e15b21549e03afcb53";
DROP INDEX IF EXISTS public."IDX_c297837543a492e58f3fed7982";
DROP INDEX IF EXISTS public."IDX_bd3a636f5eb88d1273956ebcd9";
DROP INDEX IF EXISTS public."IDX_bbd9b86138c398837390bb2167";
DROP INDEX IF EXISTS public."IDX_b87401da081a7153ec3bd1e6ab";
DROP INDEX IF EXISTS public."IDX_b7e5d981a2a0843c6a5721dc12";
DROP INDEX IF EXISTS public."IDX_b412281aebf17aeeff410e4579";
DROP INDEX IF EXISTS public."IDX_b35f7142e39a576ae2aa83764e";
DROP INDEX IF EXISTS public."IDX_b11f59fb7752d74ef9df35d090";
DROP INDEX IF EXISTS public."IDX_agency_follows_userId";
DROP INDEX IF EXISTS public."IDX_agency_follows_agencyId";
DROP INDEX IF EXISTS public."IDX_agencies_publicId";
DROP INDEX IF EXISTS public."IDX_a8f03d80e42da1162e25591dc4";
DROP INDEX IF EXISTS public."IDX_a6bb192fadb7310808004f5753";
DROP INDEX IF EXISTS public."IDX_a64905cd550435773e95786a31";
DROP INDEX IF EXISTS public."IDX_a45d7aca30d67970c093fa4eed";
DROP INDEX IF EXISTS public."IDX_a4297dabb6418274a1591c5592";
DROP INDEX IF EXISTS public."IDX_a2f99b56f95944955ff3ff26c4";
DROP INDEX IF EXISTS public."IDX_a000cca60bcf04454e72769949";
DROP INDEX IF EXISTS public."IDX_9997ca24e7f706f9d864ab36e2";
DROP INDEX IF EXISTS public."IDX_97672ac88f789774dd47f7c8be";
DROP INDEX IF EXISTS public."IDX_975c2db59c65c05fd9c6b63a2a";
DROP INDEX IF EXISTS public."IDX_96fefd8880f769fe803a9dca7c";
DROP INDEX IF EXISTS public."IDX_96c1bb2ccf043af338008825a7";
DROP INDEX IF EXISTS public."IDX_95389d5c14b7fb1ba4cd7f8405";
DROP INDEX IF EXISTS public."IDX_9519ebc45afbce61f299349a40";
DROP INDEX IF EXISTS public."IDX_947420beea7fcaae511e353fdb";
DROP INDEX IF EXISTS public."IDX_9099c98f00a1b5aca6b8f7f04a";
DROP INDEX IF EXISTS public."IDX_8ff32f632e2a009c3b4a2c9306";
DROP INDEX IF EXISTS public."IDX_8effac5e5a0f87cc71753d98e2";
DROP INDEX IF EXISTS public."IDX_8b5199ab654843357766592c0d";
DROP INDEX IF EXISTS public."IDX_8abdc3af0797198cc1edb6de94";
DROP INDEX IF EXISTS public."IDX_891986e15af0e6c9936d3d25f9";
DROP INDEX IF EXISTS public."IDX_87fdb064cf16b6ae164eb4862c";
DROP INDEX IF EXISTS public."IDX_8256f8fefaf42f4fdb204f58f4";
DROP INDEX IF EXISTS public."IDX_813db5e5b2b63149563d601d27";
DROP INDEX IF EXISTS public."IDX_7e7cbcfb689216ad5d9700657b";
DROP INDEX IF EXISTS public."IDX_7d6d35ff9fc2fd3f8ef1a47fe1";
DROP INDEX IF EXISTS public."IDX_7b92071328fdd5212b978ad248";
DROP INDEX IF EXISTS public."IDX_7ac096a8bfe8bad3810cf3b01b";
DROP INDEX IF EXISTS public."IDX_776c4d82b2a8bdf328bc3f51cc";
DROP INDEX IF EXISTS public."IDX_773d65d80dbc2f556c2b8b5804";
DROP INDEX IF EXISTS public."IDX_75e48a17f75ab2cb3104254145";
DROP INDEX IF EXISTS public."IDX_70fe713ef5463e7aa66bbcb321";
DROP INDEX IF EXISTS public."IDX_6e2c115bfdba758d450a5dc684";
DROP INDEX IF EXISTS public."IDX_6c939085068c5539bad393be6b";
DROP INDEX IF EXISTS public."IDX_69454773f1e666a14c6a953935";
DROP INDEX IF EXISTS public."IDX_692a909ee0fa9383e7859f9b40";
DROP INDEX IF EXISTS public."IDX_5f8501202b13cd3f35e2929e01";
DROP INDEX IF EXISTS public."IDX_5e82de8927486c513b9cc14374";
DROP INDEX IF EXISTS public."IDX_5e08ad421ea4742ab0b892130f";
DROP INDEX IF EXISTS public."IDX_599c2d45b92f3aca0cbb687a86";
DROP INDEX IF EXISTS public."IDX_4e6b1097a3e2f1cfa1eae0ea49";
DROP INDEX IF EXISTS public."IDX_4ddcc53b3247c634c352603dc0";
DROP INDEX IF EXISTS public."IDX_4ab3460ba2091fc0fbf66d6650";
DROP INDEX IF EXISTS public."IDX_4840b2168b71cec76465b70390";
DROP INDEX IF EXISTS public."IDX_46dc0b73131f2f646709f1b6ad";
DROP INDEX IF EXISTS public."IDX_45aef1da935b11cdf48a1adacc";
DROP INDEX IF EXISTS public."IDX_45745953065384cc9c4264c2a3";
DROP INDEX IF EXISTS public."IDX_4353be8309ce86650def2f8572";
DROP INDEX IF EXISTS public."IDX_431a010b81af0807f39bd11c84";
DROP INDEX IF EXISTS public."IDX_41d42c9f76df6bacf81bdc16e5";
DROP INDEX IF EXISTS public."IDX_3b4e13a1d9b6878a602b486ef7";
DROP INDEX IF EXISTS public."IDX_3a02b8ce593ee323902ae1403d";
DROP INDEX IF EXISTS public."IDX_33f0416f1aa1aa1976046d27ec";
DROP INDEX IF EXISTS public."IDX_32be8e6bd24d59149ebc4b29f2";
DROP INDEX IF EXISTS public."IDX_2d3f0315552537f47e08be8fc4";
DROP INDEX IF EXISTS public."IDX_29cb146a91ac8d510be26479aa";
DROP INDEX IF EXISTS public."IDX_291baf6ac9479a2645852d114a";
DROP INDEX IF EXISTS public."IDX_2873882c38e8c07d98cb64f962";
DROP INDEX IF EXISTS public."IDX_2494dde7fc5445fe3117177a39";
DROP INDEX IF EXISTS public."IDX_24051aebfe891219eaca0c1b76";
DROP INDEX IF EXISTS public."IDX_22b88b48be38104518c775a1fb";
DROP INDEX IF EXISTS public."IDX_1f792acf882931dd013fff0dab";
DROP INDEX IF EXISTS public."IDX_1ea1ca7526faba4878f91d4a3d";
DROP INDEX IF EXISTS public."IDX_1ea16c73ecef4bab2f61c31c88";
DROP INDEX IF EXISTS public."IDX_1e41a88dbde6094d3c6ebe5444";
DROP INDEX IF EXISTS public."IDX_1cca697c378ba7661c5e79dc22";
DROP INDEX IF EXISTS public."IDX_19ecab949371747d82ce365d6d";
DROP INDEX IF EXISTS public."IDX_18b4704722f74a4b3dc0ca354a";
DROP INDEX IF EXISTS public."IDX_18afd82880c01dfd863cce86ce";
DROP INDEX IF EXISTS public."IDX_1790f7a8dda278d15599817955";
DROP INDEX IF EXISTS public."IDX_14cf5cb9c3da401f4981574408";
DROP INDEX IF EXISTS public."IDX_1083039a84464289149324b7a6";
DROP INDEX IF EXISTS public."IDX_0ff026204b1ec2fe6e94ad89c1";
DROP INDEX IF EXISTS public."IDX_0e6521dead3cae78f5dfb99619";
DROP INDEX IF EXISTS public."IDX_03b0333dc1339155fe9ca667b8";
DROP INDEX IF EXISTS public."IDX_017e195ed5231583b12a25a335";
ALTER TABLE IF EXISTS ONLY public.vanity_ids DROP CONSTRAINT IF EXISTS vanity_ids_pkey;
ALTER TABLE IF EXISTS ONLY public.host_target_claims DROP CONSTRAINT IF EXISTS uq_host_target_claim;
ALTER TABLE IF EXISTS ONLY public.social_requests DROP CONSTRAINT IF EXISTS social_requests_pkey;
ALTER TABLE IF EXISTS ONLY public.slot_game_sessions DROP CONSTRAINT IF EXISTS slot_game_sessions_pkey;
ALTER TABLE IF EXISTS ONLY public.room_seat_signals DROP CONSTRAINT IF EXISTS room_seat_signals_pkey;
ALTER TABLE IF EXISTS ONLY public.room_music_tracks DROP CONSTRAINT IF EXISTS room_music_tracks_pkey;
ALTER TABLE IF EXISTS ONLY public.recharge_agent_contacts DROP CONSTRAINT IF EXISTS recharge_agent_contacts_pkey;
ALTER TABLE IF EXISTS ONLY public.profile_visits DROP CONSTRAINT IF EXISTS profile_visits_pkey;
ALTER TABLE IF EXISTS ONLY public.host_target_claims DROP CONSTRAINT IF EXISTS host_target_claims_pkey;
ALTER TABLE IF EXISTS ONLY public.gift_categories DROP CONSTRAINT IF EXISTS gift_categories_pkey;
ALTER TABLE IF EXISTS ONLY public.agency_follows DROP CONSTRAINT IF EXISTS agency_follows_pkey;
ALTER TABLE IF EXISTS ONLY public.agency_follows DROP CONSTRAINT IF EXISTS "agency_follows_agencyId_userId_key";
ALTER TABLE IF EXISTS ONLY public.agency_applications DROP CONSTRAINT IF EXISTS agency_applications_pkey;
ALTER TABLE IF EXISTS ONLY public.recharge_orders DROP CONSTRAINT IF EXISTS "UQ_recharge_provider_payment";
ALTER TABLE IF EXISTS ONLY public.recharge_orders DROP CONSTRAINT IF EXISTS "UQ_recharge_provider_order";
ALTER TABLE IF EXISTS ONLY public.recharge_agents DROP CONSTRAINT IF EXISTS "UQ_recharge_agent_user";
ALTER TABLE IF EXISTS ONLY public.payment_webhook_events DROP CONSTRAINT IF EXISTS "UQ_payment_webhook_event";
ALTER TABLE IF EXISTS ONLY public.contest_entries DROP CONSTRAINT IF EXISTS "UQ_d2f5c8524f52e9818c65a41e543";
ALTER TABLE IF EXISTS ONLY public.room_bans DROP CONSTRAINT IF EXISTS "UQ_c92968d50abe6ed1d9a3364e5f1";
ALTER TABLE IF EXISTS ONLY public.drama_reactions DROP CONSTRAINT IF EXISTS "UQ_b250958e8591404be077b7aebd7";
ALTER TABLE IF EXISTS ONLY public.agent_recharges DROP CONSTRAINT IF EXISTS "UQ_agent_recharge_idempotency";
ALTER TABLE IF EXISTS ONLY public.room_seats DROP CONSTRAINT IF EXISTS "UQ_acd51477b62103813b2cf6d1342";
ALTER TABLE IF EXISTS ONLY public.user_game_items DROP CONSTRAINT IF EXISTS "UQ_9baa7a5490b5cac05e1050115dd";
ALTER TABLE IF EXISTS ONLY public.social_requests DROP CONSTRAINT IF EXISTS "UQ_93ea00bdb5eec405388cd6dce8e";
ALTER TABLE IF EXISTS ONLY public.user_profiles DROP CONSTRAINT IF EXISTS "UQ_8481388d6325e752cd4d7e26c6d";
ALTER TABLE IF EXISTS ONLY public.room_moderators DROP CONSTRAINT IF EXISTS "UQ_7bcfcdf6f7ceef54c392600fa42";
ALTER TABLE IF EXISTS ONLY public.agency_members DROP CONSTRAINT IF EXISTS "UQ_7a8c159d139129a857092199978";
ALTER TABLE IF EXISTS ONLY public.chat_participants DROP CONSTRAINT IF EXISTS "UQ_79a34cee1c3ef6075996fda91b6";
ALTER TABLE IF EXISTS ONLY public.room_host_follows DROP CONSTRAINT IF EXISTS "UQ_7977426f9da5506c39871c2a5f8";
ALTER TABLE IF EXISTS ONLY public.host_monthly_progress DROP CONSTRAINT IF EXISTS "UQ_7426b29b4943552abef40db8b59";
ALTER TABLE IF EXISTS ONLY public.room_seat_signals DROP CONSTRAINT IF EXISTS "UQ_5679d603a3f0b0e78181a4af2b4";
ALTER TABLE IF EXISTS ONLY public.plaza_event_subscriptions DROP CONSTRAINT IF EXISTS "UQ_51935bd821dbdfc63b928a17542";
ALTER TABLE IF EXISTS ONLY public.blocks DROP CONSTRAINT IF EXISTS "UQ_4abe7bad89347a663fc8f428d0d";
ALTER TABLE IF EXISTS ONLY public.wallets DROP CONSTRAINT IF EXISTS "UQ_2ecdb33f23e9a6fc392025c0b97";
ALTER TABLE IF EXISTS ONLY public.profile_visits DROP CONSTRAINT IF EXISTS "UQ_1dfab9da5d08380dc9515561046";
ALTER TABLE IF EXISTS ONLY public.follows DROP CONSTRAINT IF EXISTS "UQ_105079775692df1f8799ed0fac8";
ALTER TABLE IF EXISTS ONLY public.lucky_reward_grants DROP CONSTRAINT IF EXISTS "REL_9519ebc45afbce61f299349a40";
ALTER TABLE IF EXISTS ONLY public.chat_conversations DROP CONSTRAINT IF EXISTS "PK_ff117d9f57807c4f2e3034a39f3";
ALTER TABLE IF EXISTS ONLY public.room_bans DROP CONSTRAINT IF EXISTS "PK_f9d925f0f4d6ce338e967a299e0";
ALTER TABLE IF EXISTS ONLY public.room_seats DROP CONSTRAINT IF EXISTS "PK_f7eb712e647c078989f5d82485d";
ALTER TABLE IF EXISTS ONLY public.room_games DROP CONSTRAINT IF EXISTS "PK_f4ccf210273a62f07cdcdd75d0d";
ALTER TABLE IF EXISTS ONLY public.lucky_boxes DROP CONSTRAINT IF EXISTS "PK_f28f77ccf0cd86ded8993a83ecd";
ALTER TABLE IF EXISTS ONLY public.chat_participants DROP CONSTRAINT IF EXISTS "PK_ebf68c52a2b4dceb777672b782d";
ALTER TABLE IF EXISTS ONLY public.user_promo_progress DROP CONSTRAINT IF EXISTS "PK_eb66d6a1fd6e8a6109765569ab6";
ALTER TABLE IF EXISTS ONLY public.room_moderators DROP CONSTRAINT IF EXISTS "PK_eb5311c5c35ab7b7e8a94662418";
ALTER TABLE IF EXISTS ONLY public.reports DROP CONSTRAINT IF EXISTS "PK_d9013193989303580053c0b5ef6";
ALTER TABLE IF EXISTS ONLY public.lucky_reward_grants DROP CONSTRAINT IF EXISTS "PK_cc275bfe953b2e866f52d50752f";
ALTER TABLE IF EXISTS ONLY public.user_vips DROP CONSTRAINT IF EXISTS "PK_c84a4422f457962181145c047b1";
ALTER TABLE IF EXISTS ONLY public.plaza_event_subscriptions DROP CONSTRAINT IF EXISTS "PK_c3482dc3b92266719eb2a64a01c";
ALTER TABLE IF EXISTS ONLY public.room_chat_penalties DROP CONSTRAINT IF EXISTS "PK_c2151e64c2dbe9fb92022d8bf64";
ALTER TABLE IF EXISTS ONLY public.devices DROP CONSTRAINT IF EXISTS "PK_b1514758245c12daf43486dd1f0";
ALTER TABLE IF EXISTS ONLY public.drama_reactions DROP CONSTRAINT IF EXISTS "PK_ac087a86f783bc8d873220da0a3";
ALTER TABLE IF EXISTS ONLY public.abuse_logs DROP CONSTRAINT IF EXISTS "PK_aa2916720a09f5d9c716da05bff";
ALTER TABLE IF EXISTS ONLY public.user_game_items DROP CONSTRAINT IF EXISTS "PK_a4be1b76282d18a3eadd1e41d7f";
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS "PK_a3ffb1c0c8416b9fc6f907b7433";
ALTER TABLE IF EXISTS ONLY public.drama_episodes DROP CONSTRAINT IF EXISTS "PK_a031dfde0ceeb2b3b3dad9398c8";
ALTER TABLE IF EXISTS ONLY public.otp_codes DROP CONSTRAINT IF EXISTS "PK_9d0487965ac1837d57fec4d6a26";
ALTER TABLE IF EXISTS ONLY public.contest_entries DROP CONSTRAINT IF EXISTS "PK_94074f3f5cc3ee7b5763a6041b1";
ALTER TABLE IF EXISTS ONLY public.female_identity_verifications DROP CONSTRAINT IF EXISTS "PK_8b4687c9d3fb7155f2af4bf919e";
ALTER TABLE IF EXISTS ONLY public.agencies DROP CONSTRAINT IF EXISTS "PK_8ab1f1f53f56c8255b0d7e68b28";
ALTER TABLE IF EXISTS ONLY public.host_monthly_progress DROP CONSTRAINT IF EXISTS "PK_8aa4f101d579ad979edc800e73c";
ALTER TABLE IF EXISTS ONLY public.follows DROP CONSTRAINT IF EXISTS "PK_8988f607744e16ff79da3b8a627";
ALTER TABLE IF EXISTS ONLY public.gift_sends DROP CONSTRAINT IF EXISTS "PK_86f0103cb304318f377b7395694";
ALTER TABLE IF EXISTS ONLY public.agency_members DROP CONSTRAINT IF EXISTS "PK_868eae9a18f43f0a9a810bfe981";
ALTER TABLE IF EXISTS ONLY public.wallets DROP CONSTRAINT IF EXISTS "PK_8402e5df5a30a229380e83e4f7e";
ALTER TABLE IF EXISTS ONLY public.blocks DROP CONSTRAINT IF EXISTS "PK_8244fa1495c4e9222a01059244b";
ALTER TABLE IF EXISTS ONLY public.room_access DROP CONSTRAINT IF EXISTS "PK_8120c22097d247ea41f4f0df7d5";
ALTER TABLE IF EXISTS ONLY public.payment_webhook_events DROP CONSTRAINT IF EXISTS "PK_750875e71d97974be92cee813ba";
ALTER TABLE IF EXISTS ONLY public.drama_series DROP CONSTRAINT IF EXISTS "PK_6ff9d2807bc6d716adb7a775cfe";
ALTER TABLE IF EXISTS ONLY public.cosmetics DROP CONSTRAINT IF EXISTS "PK_6f1c3811930a2ebd6f7d1a49ad0";
ALTER TABLE IF EXISTS ONLY public.notifications DROP CONSTRAINT IF EXISTS "PK_6a72c3c0f683f6462415e653c3a";
ALTER TABLE IF EXISTS ONLY public.user_cosmetics DROP CONSTRAINT IF EXISTS "PK_69f95df2183a52e7c06d24d9fb4";
ALTER TABLE IF EXISTS ONLY public.ranking_snapshots DROP CONSTRAINT IF EXISTS "PK_6253f2250aeb3c754d80821ec06";
ALTER TABLE IF EXISTS ONLY public.recharge_agent_applications DROP CONSTRAINT IF EXISTS "PK_571a232e4a09622f7e8a12523a1";
ALTER TABLE IF EXISTS ONLY public.vip_plans DROP CONSTRAINT IF EXISTS "PK_557f673f1e6d5a6f9ba2dc1782f";
ALTER TABLE IF EXISTS ONLY public.gifts DROP CONSTRAINT IF EXISTS "PK_54242922934e1f322861d116af7";
ALTER TABLE IF EXISTS ONLY public.wallet_transactions DROP CONSTRAINT IF EXISTS "PK_5120f131bde2cda940ec1a621db";
ALTER TABLE IF EXISTS ONLY public.plaza_events DROP CONSTRAINT IF EXISTS "PK_50c4f55b973c9aed6d46dcb9da8";
ALTER TABLE IF EXISTS ONLY public.app_settings DROP CONSTRAINT IF EXISTS "PK_4800b266ba790931744b3e53a74";
ALTER TABLE IF EXISTS ONLY public.chat_messages DROP CONSTRAINT IF EXISTS "PK_40c55ee0e571e268b0d3cd37d10";
ALTER TABLE IF EXISTS ONLY public.recharge_agents DROP CONSTRAINT IF EXISTS "PK_3fd65b031f25be5bb55a069e304";
ALTER TABLE IF EXISTS ONLY public.recharge_orders DROP CONSTRAINT IF EXISTS "PK_3e35ca3c8600b9d9642d7b5f525";
ALTER TABLE IF EXISTS ONLY public.agent_recharges DROP CONSTRAINT IF EXISTS "PK_2e4d7dff1129f985eea83e1c712";
ALTER TABLE IF EXISTS ONLY public.withdraw_requests DROP CONSTRAINT IF EXISTS "PK_2dfe600c271c8d99162ef7dddbf";
ALTER TABLE IF EXISTS ONLY public.recharge_agent_ledger DROP CONSTRAINT IF EXISTS "PK_2dcac7d93905be88a37f29a1abe";
ALTER TABLE IF EXISTS ONLY public.room_host_follows DROP CONSTRAINT IF EXISTS "PK_2d0dee47cbf5dc4efc50014881b";
ALTER TABLE IF EXISTS ONLY public.user_profiles DROP CONSTRAINT IF EXISTS "PK_1ec6662219f4605723f1e41b6cb";
ALTER TABLE IF EXISTS ONLY public.lucky_box_opens DROP CONSTRAINT IF EXISTS "PK_11c0c1026d79ca3c29240b37a17";
ALTER TABLE IF EXISTS ONLY public.contests DROP CONSTRAINT IF EXISTS "PK_0b8012f5cf6f444a52179e1227a";
ALTER TABLE IF EXISTS ONLY public.admin_users DROP CONSTRAINT IF EXISTS "PK_06744d221bb6145dc61e5dc441d";
ALTER TABLE IF EXISTS ONLY public.rooms DROP CONSTRAINT IF EXISTS "PK_0368a2d7c215f2d0458a54933f2";
DROP TABLE IF EXISTS public.withdraw_requests;
DROP TABLE IF EXISTS public.wallets;
DROP TABLE IF EXISTS public.wallet_transactions;
DROP TABLE IF EXISTS public.vip_plans;
DROP TABLE IF EXISTS public.vanity_ids;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.user_vips;
DROP TABLE IF EXISTS public.user_promo_progress;
DROP TABLE IF EXISTS public.user_profiles;
DROP TABLE IF EXISTS public.user_game_items;
DROP TABLE IF EXISTS public.user_cosmetics;
DROP TABLE IF EXISTS public.social_requests;
DROP TABLE IF EXISTS public.slot_game_sessions;
DROP SEQUENCE IF EXISTS public.rooms_public_id_seq;
DROP TABLE IF EXISTS public.rooms;
DROP TABLE IF EXISTS public.room_seats;
DROP TABLE IF EXISTS public.room_seat_signals;
DROP TABLE IF EXISTS public.room_music_tracks;
DROP TABLE IF EXISTS public.room_moderators;
DROP TABLE IF EXISTS public.room_host_follows;
DROP TABLE IF EXISTS public.room_games;
DROP TABLE IF EXISTS public.room_chat_penalties;
DROP TABLE IF EXISTS public.room_bans;
DROP TABLE IF EXISTS public.room_access;
DROP TABLE IF EXISTS public.reports;
DROP TABLE IF EXISTS public.recharge_orders;
DROP TABLE IF EXISTS public.recharge_agents;
DROP TABLE IF EXISTS public.recharge_agent_ledger;
DROP TABLE IF EXISTS public.recharge_agent_contacts;
DROP TABLE IF EXISTS public.recharge_agent_applications;
DROP TABLE IF EXISTS public.ranking_snapshots;
DROP TABLE IF EXISTS public.profile_visits;
DROP TABLE IF EXISTS public.plaza_events;
DROP TABLE IF EXISTS public.plaza_event_subscriptions;
DROP TABLE IF EXISTS public.payment_webhook_events;
DROP TABLE IF EXISTS public.otp_codes;
DROP TABLE IF EXISTS public.notifications;
DROP TABLE IF EXISTS public.lucky_reward_grants;
DROP TABLE IF EXISTS public.lucky_boxes;
DROP TABLE IF EXISTS public.lucky_box_opens;
DROP TABLE IF EXISTS public.host_target_claims;
DROP TABLE IF EXISTS public.host_monthly_progress;
DROP TABLE IF EXISTS public.gifts;
DROP TABLE IF EXISTS public.gift_sends;
DROP TABLE IF EXISTS public.gift_categories;
DROP TABLE IF EXISTS public.follows;
DROP TABLE IF EXISTS public.female_identity_verifications;
DROP TABLE IF EXISTS public.drama_series;
DROP TABLE IF EXISTS public.drama_reactions;
DROP TABLE IF EXISTS public.drama_episodes;
DROP TABLE IF EXISTS public.devices;
DROP TABLE IF EXISTS public.cosmetics;
DROP TABLE IF EXISTS public.contests;
DROP TABLE IF EXISTS public.contest_entries;
DROP TABLE IF EXISTS public.chat_participants;
DROP TABLE IF EXISTS public.chat_messages;
DROP TABLE IF EXISTS public.chat_conversations;
DROP TABLE IF EXISTS public.blocks;
DROP TABLE IF EXISTS public.app_settings;
DROP TABLE IF EXISTS public.agent_recharges;
DROP TABLE IF EXISTS public.agency_members;
DROP TABLE IF EXISTS public.agency_follows;
DROP TABLE IF EXISTS public.agency_applications;
DROP TABLE IF EXISTS public.agencies;
DROP TABLE IF EXISTS public.admin_users;
DROP TABLE IF EXISTS public.abuse_logs;
DROP TYPE IF EXISTS public.withdraw_requests_status_enum;
DROP TYPE IF EXISTS public.wallet_transactions_type_enum;
DROP TYPE IF EXISTS public.wallet_transactions_currency_enum;
DROP TYPE IF EXISTS public.users_status_enum;
DROP TYPE IF EXISTS public.users_gender_enum;
DROP TYPE IF EXISTS public.rooms_type_enum;
DROP TYPE IF EXISTS public.rooms_status_enum;
DROP TYPE IF EXISTS public.room_seats_status_enum;
DROP TYPE IF EXISTS public.room_moderators_role_enum;
DROP TYPE IF EXISTS public.room_games_type_enum;
DROP TYPE IF EXISTS public.room_games_status_enum;
DROP TYPE IF EXISTS public.reports_targettype_enum;
DROP TYPE IF EXISTS public.reports_status_enum;
DROP TYPE IF EXISTS public.recharge_orders_status_enum;
DROP TYPE IF EXISTS public.recharge_orders_provider_enum;
DROP TYPE IF EXISTS public.recharge_agents_status_enum;
DROP TYPE IF EXISTS public.recharge_agents_source_enum;
DROP TYPE IF EXISTS public.recharge_agent_status_enum;
DROP TYPE IF EXISTS public.recharge_agent_source_enum;
DROP TYPE IF EXISTS public.recharge_agent_ledger_type_enum;
DROP TYPE IF EXISTS public.recharge_agent_applications_status_enum;
DROP TYPE IF EXISTS public.ranking_snapshots_period_enum;
DROP TYPE IF EXISTS public.ranking_snapshots_category_enum;
DROP TYPE IF EXISTS public.pk_battles_status_enum;
DROP TYPE IF EXISTS public.otp_codes_purpose_enum;
DROP TYPE IF EXISTS public.notifications_type_enum;
DROP TYPE IF EXISTS public.live_streams_status_enum;
DROP TYPE IF EXISTS public.live_streams_mode_enum;
DROP TYPE IF EXISTS public.gifts_type_enum;
DROP TYPE IF EXISTS public.female_identity_verifications_status_enum;
DROP TYPE IF EXISTS public.cosmetics_type_enum;
DROP TYPE IF EXISTS public.chat_messages_type_enum;
DROP TYPE IF EXISTS public.chat_conversations_type_enum;
DROP TYPE IF EXISTS public.agent_ledger_type_enum;
DROP TYPE IF EXISTS public.agency_members_role_enum;
DROP TYPE IF EXISTS public.agencies_status_enum;
DROP TYPE IF EXISTS public.admin_users_role_enum;
DROP TYPE IF EXISTS public.abuse_logs_severity_enum;
DROP EXTENSION IF EXISTS "uuid-ossp";
DROP EXTENSION IF EXISTS pgcrypto;
--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: abuse_logs_severity_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.abuse_logs_severity_enum AS ENUM (
    'low',
    'medium',
    'high',
    'critical'
);


--
-- Name: admin_users_role_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.admin_users_role_enum AS ENUM (
    'super',
    'moderator',
    'support',
    'finance'
);


--
-- Name: agencies_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.agencies_status_enum AS ENUM (
    'pending',
    'active',
    'suspended',
    'rejected'
);


--
-- Name: agency_members_role_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.agency_members_role_enum AS ENUM (
    'owner',
    'manager',
    'host',
    'member'
);


--
-- Name: agent_ledger_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.agent_ledger_type_enum AS ENUM (
    'float_credit',
    'float_debit',
    'sale',
    'commission',
    'adjustment'
);


--
-- Name: chat_conversations_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.chat_conversations_type_enum AS ENUM (
    'direct',
    'group'
);


--
-- Name: chat_messages_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.chat_messages_type_enum AS ENUM (
    'text',
    'image',
    'video',
    'audio',
    'file',
    'gift',
    'system'
);


--
-- Name: cosmetics_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.cosmetics_type_enum AS ENUM (
    'entry_effect',
    'join_toast',
    'room_card',
    'room_background',
    'level_badge',
    'vip_badge',
    'host_badge'
);


--
-- Name: female_identity_verifications_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.female_identity_verifications_status_enum AS ENUM (
    'pending',
    'approved',
    'rejected'
);


--
-- Name: gifts_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.gifts_type_enum AS ENUM (
    'normal',
    'lucky',
    'combo',
    'premium'
);


--
-- Name: live_streams_mode_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.live_streams_mode_enum AS ENUM (
    'single',
    'pk',
    'multi_guest'
);


--
-- Name: live_streams_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.live_streams_status_enum AS ENUM (
    'scheduled',
    'live',
    'ended',
    'banned'
);


--
-- Name: notifications_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.notifications_type_enum AS ENUM (
    'system',
    'follow',
    'gift',
    'room',
    'chat',
    'vip',
    'agency',
    'wallet'
);


--
-- Name: otp_codes_purpose_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.otp_codes_purpose_enum AS ENUM (
    'login',
    'register',
    'reset_password',
    'verify_phone',
    'verify_email'
);


--
-- Name: pk_battles_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.pk_battles_status_enum AS ENUM (
    'pending',
    'active',
    'ended',
    'cancelled'
);


--
-- Name: ranking_snapshots_category_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.ranking_snapshots_category_enum AS ENUM (
    'rich',
    'popular',
    'host',
    'agency',
    'room',
    'gifts'
);


--
-- Name: ranking_snapshots_period_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.ranking_snapshots_period_enum AS ENUM (
    'daily',
    'weekly',
    'monthly'
);


--
-- Name: recharge_agent_applications_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_agent_applications_status_enum AS ENUM (
    'pending',
    'active',
    'suspended',
    'rejected'
);


--
-- Name: recharge_agent_ledger_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_agent_ledger_type_enum AS ENUM (
    'float_credit',
    'float_debit',
    'sale',
    'commission',
    'adjustment'
);


--
-- Name: recharge_agent_source_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_agent_source_enum AS ENUM (
    'admin',
    'application'
);


--
-- Name: recharge_agent_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_agent_status_enum AS ENUM (
    'pending',
    'active',
    'suspended',
    'rejected'
);


--
-- Name: recharge_agents_source_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_agents_source_enum AS ENUM (
    'admin',
    'application'
);


--
-- Name: recharge_agents_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_agents_status_enum AS ENUM (
    'pending',
    'active',
    'suspended',
    'rejected'
);


--
-- Name: recharge_orders_provider_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_orders_provider_enum AS ENUM (
    'stripe',
    'paypal',
    'google_play',
    'apple',
    'crypto',
    'admin',
    'binance_pay',
    'binance_wallet',
    'recharge_agent',
    'fourthwall',
    'sham_cash'
);


--
-- Name: recharge_orders_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recharge_orders_status_enum AS ENUM (
    'pending',
    'completed',
    'failed',
    'cancelled',
    'refunded'
);


--
-- Name: reports_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reports_status_enum AS ENUM (
    'pending',
    'reviewing',
    'resolved',
    'rejected'
);


--
-- Name: reports_targettype_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reports_targettype_enum AS ENUM (
    'user',
    'room',
    'message',
    'gift'
);


--
-- Name: room_games_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.room_games_status_enum AS ENUM (
    'waiting',
    'playing',
    'finished',
    'cancelled'
);


--
-- Name: room_games_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.room_games_type_enum AS ENUM (
    'tic_tac_toe'
);


--
-- Name: room_moderators_role_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.room_moderators_role_enum AS ENUM (
    'mod',
    'admin'
);


--
-- Name: room_seats_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.room_seats_status_enum AS ENUM (
    'empty',
    'occupied',
    'locked',
    'muted'
);


--
-- Name: rooms_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.rooms_status_enum AS ENUM (
    'open',
    'locked',
    'closed'
);


--
-- Name: rooms_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.rooms_type_enum AS ENUM (
    'voice',
    'party'
);


--
-- Name: users_gender_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.users_gender_enum AS ENUM (
    'male',
    'female',
    'other',
    'unspecified'
);


--
-- Name: users_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.users_status_enum AS ENUM (
    'active',
    'banned',
    'deleted',
    'suspended'
);


--
-- Name: wallet_transactions_currency_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.wallet_transactions_currency_enum AS ENUM (
    'coins',
    'diamonds'
);


--
-- Name: wallet_transactions_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.wallet_transactions_type_enum AS ENUM (
    'recharge',
    'gift_send',
    'gift_receive',
    'exchange',
    'withdraw',
    'refund',
    'admin_adjust',
    'lucky_reward',
    'invite_reward',
    'room_entry',
    'drama_watch',
    'game_ad_reward'
);


--
-- Name: withdraw_requests_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.withdraw_requests_status_enum AS ENUM (
    'pending',
    'approved',
    'rejected',
    'paid',
    'cancelled'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: abuse_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abuse_logs (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid,
    action character varying(64) NOT NULL,
    "targetType" character varying(64),
    "targetId" uuid,
    severity public.abuse_logs_severity_enum DEFAULT 'low'::public.abuse_logs_severity_enum NOT NULL,
    details text,
    "ipAddress" character varying(64),
    resolved boolean DEFAULT false NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: admin_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_users (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    email character varying(64) NOT NULL,
    username character varying(48) NOT NULL,
    "passwordHash" character varying(255) NOT NULL,
    role public.admin_users_role_enum DEFAULT 'moderator'::public.admin_users_role_enum NOT NULL,
    "linkedUserId" uuid,
    "isActive" boolean DEFAULT true NOT NULL,
    "lastLoginAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agencies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agencies (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    name character varying(128) NOT NULL,
    description text,
    "logoUrl" character varying(512),
    "ownerId" uuid NOT NULL,
    status public.agencies_status_enum DEFAULT 'pending'::public.agencies_status_enum NOT NULL,
    "memberCount" integer DEFAULT 0 NOT NULL,
    "totalDiamonds" bigint DEFAULT '0'::bigint NOT NULL,
    "commissionPercent" numeric(5,2) DEFAULT '20'::numeric NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "activationCode" character varying(16),
    "notificationStyle" character varying(32) DEFAULT 'welcome'::character varying NOT NULL,
    "publicId" character varying(16),
    "isVerified" boolean DEFAULT false NOT NULL,
    "verifiedAt" timestamp with time zone,
    "exclusiveFrameCode" character varying(64),
    "exclusiveRoomCardCode" character varying(64),
    "exclusiveFrameUrl" character varying(512)
);


--
-- Name: agency_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agency_applications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "applicantId" uuid NOT NULL,
    "proposedName" character varying(128) NOT NULL,
    description text NOT NULL,
    "businessPlan" text NOT NULL,
    country character varying(100) NOT NULL,
    "contactEmail" character varying(254) NOT NULL,
    "contactPhone" character varying(32) NOT NULL,
    "socialLink" character varying(512),
    experience text NOT NULL,
    "expectedHostCount" integer NOT NULL,
    "documentUrls" jsonb DEFAULT '[]'::jsonb NOT NULL,
    "termsAccepted" boolean DEFAULT false NOT NULL,
    "reviewNote" text,
    "reviewedById" uuid,
    "agencyId" uuid,
    "reviewedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(24) DEFAULT 'pending'::character varying NOT NULL,
    "paidCoins" bigint DEFAULT '0'::bigint NOT NULL,
    "paymentReferenceId" character varying(128),
    "paymentRefunded" boolean DEFAULT false NOT NULL
);


--
-- Name: agency_follows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agency_follows (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "agencyId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: agency_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agency_members (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "agencyId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    role public.agency_members_role_enum DEFAULT 'member'::public.agency_members_role_enum NOT NULL,
    "diamondsContributed" bigint DEFAULT '0'::bigint NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "joinedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying NOT NULL
);


--
-- Name: agent_recharges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_recharges (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "agentId" uuid NOT NULL,
    "recipientUserId" uuid NOT NULL,
    sku character varying(64),
    coins integer NOT NULL,
    "chargedFloat" integer NOT NULL,
    "commissionCoins" integer DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'completed'::character varying NOT NULL,
    "idempotencyKey" character varying(96) NOT NULL,
    "walletTxId" uuid,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: app_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_settings (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    key character varying(128) NOT NULL,
    value text NOT NULL,
    description character varying(255),
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: blocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blocks (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "blockerId" uuid NOT NULL,
    "blockedId" uuid NOT NULL,
    reason character varying(255),
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_conversations (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    type public.chat_conversations_type_enum DEFAULT 'direct'::public.chat_conversations_type_enum NOT NULL,
    title character varying(128),
    "directKey" character varying(128),
    "lastMessageId" uuid,
    "lastMessageAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_messages (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "conversationId" uuid NOT NULL,
    "senderId" uuid NOT NULL,
    type public.chat_messages_type_enum DEFAULT 'text'::public.chat_messages_type_enum NOT NULL,
    content text,
    media text,
    "replyToId" uuid,
    "forwardedFromId" uuid,
    "isEdited" boolean DEFAULT false NOT NULL,
    "isUnsent" boolean DEFAULT false NOT NULL,
    "editedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_participants (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "conversationId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "isMuted" boolean DEFAULT false NOT NULL,
    "isPinned" boolean DEFAULT false NOT NULL,
    "lastReadAt" timestamp with time zone,
    "unreadCount" integer DEFAULT 0 NOT NULL,
    "joinedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "isArchived" boolean DEFAULT false NOT NULL,
    "isDeleted" boolean DEFAULT false NOT NULL
);


--
-- Name: contest_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contest_entries (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "contestId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    score bigint DEFAULT '0'::bigint NOT NULL,
    "joinedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: contests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contests (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title character varying(128) NOT NULL,
    description character varying(512),
    category character varying(32) DEFAULT 'rich'::character varying NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    "entryFeeCoins" integer DEFAULT 0 NOT NULL,
    "prizeCoins" integer DEFAULT 0 NOT NULL,
    "prizeLabel" character varying(128),
    "startAt" timestamp with time zone NOT NULL,
    "endAt" timestamp with time zone NOT NULL,
    "entrantsCount" integer DEFAULT 0 NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    scope character varying(16) DEFAULT 'global'::character varying NOT NULL,
    "roomId" uuid,
    "agencyId" uuid
);


--
-- Name: cosmetics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cosmetics (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    type public.cosmetics_type_enum NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(255),
    "previewUrl" character varying(512) NOT NULL,
    "animationUrl" character varying(512),
    "coinPrice" integer DEFAULT 0 NOT NULL,
    "minVipLevel" integer DEFAULT 0 NOT NULL,
    "minUserLevel" integer DEFAULT 0 NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "sortOrder" integer DEFAULT 0 NOT NULL,
    meta text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.devices (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "deviceId" character varying(128) NOT NULL,
    platform character varying(32) NOT NULL,
    model character varying(64),
    "fcmToken" character varying(512),
    "appVersion" character varying(64),
    "isActive" boolean DEFAULT true NOT NULL,
    "lastSeenAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: drama_episodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.drama_episodes (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "seriesId" uuid NOT NULL,
    title character varying(200) NOT NULL,
    "episodeNumber" integer DEFAULT 1 NOT NULL,
    "videoUrl" character varying(1024) NOT NULL,
    "thumbnailUrl" character varying(512),
    "durationSec" integer DEFAULT 0 NOT NULL,
    "viewCount" integer DEFAULT 0 NOT NULL,
    "likeCount" integer DEFAULT 0 NOT NULL,
    "isPublished" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: drama_reactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.drama_reactions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "targetType" character varying(16) NOT NULL,
    "targetId" uuid NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: drama_series; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.drama_series (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    "coverUrl" character varying(512),
    category character varying(64),
    "isPublished" boolean DEFAULT true NOT NULL,
    "isFeatured" boolean DEFAULT false NOT NULL,
    "episodeCount" integer DEFAULT 0 NOT NULL,
    "totalViews" integer DEFAULT 0 NOT NULL,
    "totalLikes" integer DEFAULT 0 NOT NULL,
    "sortOrder" integer DEFAULT 0 NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: female_identity_verifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.female_identity_verifications (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "selfieUrl" character varying(512) NOT NULL,
    status public.female_identity_verifications_status_enum DEFAULT 'pending'::public.female_identity_verifications_status_enum NOT NULL,
    "reviewNote" text,
    "reviewedBy" uuid,
    "reviewedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "livenessScore" double precision,
    "livenessPassed" boolean DEFAULT false NOT NULL,
    "decisionMode" character varying(16)
);


--
-- Name: follows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.follows (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "followerId" uuid NOT NULL,
    "followingId" uuid NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: gift_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gift_categories (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    key character varying(32) NOT NULL,
    "labelAr" character varying(64) NOT NULL,
    "labelEn" character varying(64),
    "sortOrder" integer DEFAULT 0 NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "iconUrl" character varying(512),
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: gift_sends; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gift_sends (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "giftId" uuid,
    "senderId" uuid NOT NULL,
    "receiverId" uuid NOT NULL,
    "roomId" uuid,
    quantity integer DEFAULT 1 NOT NULL,
    "comboCount" integer DEFAULT 1 NOT NULL,
    "totalCoins" integer NOT NULL,
    "diamondsAwarded" integer DEFAULT 0 NOT NULL,
    "luckyMultiplier" numeric(10,2),
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "giftName" character varying(128),
    "giftIconUrl" character varying(512)
);


--
-- Name: gifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gifts (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    name character varying(64) NOT NULL,
    description character varying(255),
    "iconUrl" character varying(512) NOT NULL,
    "animationUrl" character varying(512),
    "coinPrice" integer NOT NULL,
    "diamondValue" integer DEFAULT 0 NOT NULL,
    type public.gifts_type_enum DEFAULT 'normal'::public.gifts_type_enum NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "sortOrder" integer DEFAULT 0 NOT NULL,
    "comboWindowMs" integer,
    "luckyConfig" text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    category character varying(32) DEFAULT 'normal'::character varying NOT NULL,
    "brandAgencyId" uuid
);


--
-- Name: host_monthly_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.host_monthly_progress (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "yearMonth" character varying(7) NOT NULL,
    progress bigint DEFAULT '0'::bigint NOT NULL,
    "claimedStageIds" text DEFAULT '[]'::text NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "cyclesCompleted" integer DEFAULT 0 NOT NULL,
    "withdrawnStageIds" text DEFAULT '[]'::text
);


--
-- Name: host_target_claims; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.host_target_claims (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "userId" uuid NOT NULL,
    "yearMonth" character varying(7) NOT NULL,
    "stageIndex" integer NOT NULL,
    "rewardCoins" bigint DEFAULT 0 NOT NULL,
    "rewardDiamonds" bigint DEFAULT 0 NOT NULL,
    "claimedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: lucky_box_opens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lucky_box_opens (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "boxId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "roomId" uuid,
    "agencyId" uuid,
    "costPaid" integer DEFAULT 0 NOT NULL,
    "rewardType" character varying(32) NOT NULL,
    "rewardAmount" bigint NOT NULL,
    "dayKey" character varying(10) NOT NULL,
    metadata jsonb,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: lucky_boxes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lucky_boxes (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    code character varying(64) NOT NULL,
    title character varying(128) NOT NULL,
    kind character varying(32) DEFAULT 'free_daily'::character varying NOT NULL,
    "costCoins" integer DEFAULT 0 NOT NULL,
    "dailyLimitPerUser" integer DEFAULT 1 NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "rewardsJson" jsonb DEFAULT '[]'::jsonb NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: lucky_reward_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lucky_reward_grants (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "openId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "rewardType" character varying(32) NOT NULL,
    amount bigint NOT NULL,
    "walletTxId" uuid,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    type public.notifications_type_enum NOT NULL,
    title character varying(128) NOT NULL,
    body text NOT NULL,
    data text,
    "isRead" boolean DEFAULT false NOT NULL,
    "fcmSent" boolean DEFAULT false NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: otp_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.otp_codes (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    target character varying(64) NOT NULL,
    channel character varying(16) NOT NULL,
    purpose public.otp_codes_purpose_enum NOT NULL,
    "codeHash" character varying(128) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    used boolean DEFAULT false NOT NULL,
    "expiresAt" timestamp with time zone NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payment_webhook_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_webhook_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    provider character varying(32) NOT NULL,
    "eventId" character varying(191) NOT NULL,
    "eventType" character varying(64),
    "orderId" uuid,
    payload text,
    processed boolean DEFAULT false NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: plaza_event_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plaza_event_subscriptions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "eventId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: plaza_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plaza_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title character varying(128) NOT NULL,
    description character varying(512),
    "coverUrl" character varying(512),
    tag character varying(32) DEFAULT 'party'::character varying NOT NULL,
    status character varying(16) DEFAULT 'upcoming'::character varying NOT NULL,
    "startAt" timestamp with time zone NOT NULL,
    "endAt" timestamp with time zone NOT NULL,
    "hostId" uuid NOT NULL,
    "roomId" uuid,
    "subscribersCount" integer DEFAULT 0 NOT NULL,
    "isPublic" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: profile_visits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profile_visits (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "visitorId" uuid NOT NULL,
    "profileUserId" uuid NOT NULL,
    "visitCount" integer DEFAULT 1 NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "lastVisitedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: ranking_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ranking_snapshots (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    period public.ranking_snapshots_period_enum NOT NULL,
    category public.ranking_snapshots_category_enum NOT NULL,
    "periodKey" character varying(32) NOT NULL,
    "targetId" uuid NOT NULL,
    "targetName" character varying(128),
    rank integer NOT NULL,
    score bigint NOT NULL,
    meta text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: recharge_agent_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_agent_applications (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    contact character varying(128),
    region character varying(64),
    reason text,
    status public.recharge_agent_applications_status_enum DEFAULT 'pending'::public.recharge_agent_applications_status_enum NOT NULL,
    "reviewNote" text,
    "reviewedBy" uuid,
    "reviewedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "requestedCoins" bigint DEFAULT 0 NOT NULL,
    "membershipFeeUsdt" numeric(18,8) DEFAULT 0 NOT NULL,
    "stockCostUsdt" numeric(18,8) DEFAULT 0 NOT NULL,
    "totalPaidUsdt" numeric(18,8) DEFAULT 0 NOT NULL,
    "paymentNetwork" character varying(16),
    "paymentReference" character varying(160)
);


--
-- Name: recharge_agent_contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_agent_contacts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "displayName" character varying(80) NOT NULL,
    country character varying(64) NOT NULL,
    whatsapp character varying(64),
    telegram character varying(64),
    notes text,
    "isActive" boolean DEFAULT true NOT NULL,
    "sortOrder" integer DEFAULT 0 NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: recharge_agent_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_agent_ledger (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "agentId" uuid NOT NULL,
    type public.recharge_agent_ledger_type_enum NOT NULL,
    amount bigint NOT NULL,
    "balanceAfter" bigint NOT NULL,
    "referenceId" uuid,
    "referenceType" character varying(64),
    description text,
    "actorUserId" uuid,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: recharge_agents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_agents (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    status public.recharge_agents_status_enum DEFAULT 'pending'::public.recharge_agents_status_enum NOT NULL,
    source public.recharge_agents_source_enum DEFAULT 'admin'::public.recharge_agents_source_enum NOT NULL,
    "floatCoins" bigint DEFAULT '0'::bigint NOT NULL,
    "commissionBps" integer DEFAULT 0 NOT NULL,
    "dailyLimitCoins" integer DEFAULT 500000 NOT NULL,
    "dailySoldCoins" integer DEFAULT 0 NOT NULL,
    "dailySoldKey" character varying(16),
    notes text,
    "reviewedBy" uuid,
    "reviewedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    country character varying(64),
    whatsapp character varying(64),
    telegram character varying(64),
    "listedInDirectory" boolean DEFAULT false NOT NULL
);


--
-- Name: recharge_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recharge_orders (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    coins integer NOT NULL,
    "amountFiat" numeric(30,8) NOT NULL,
    provider public.recharge_orders_provider_enum NOT NULL,
    status public.recharge_orders_status_enum DEFAULT 'pending'::public.recharge_orders_status_enum NOT NULL,
    "providerOrderId" character varying(255),
    "providerPaymentId" character varying(255),
    "providerPayload" text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    sku character varying(64),
    "bonusCoins" integer DEFAULT 0 NOT NULL,
    "expiresAt" timestamp with time zone,
    "completedAt" timestamp with time zone,
    currency character varying(16) DEFAULT 'USD'::character varying NOT NULL
);


--
-- Name: reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "reporterId" uuid NOT NULL,
    "targetType" public.reports_targettype_enum NOT NULL,
    "targetId" uuid NOT NULL,
    reason character varying(64) NOT NULL,
    description text,
    "evidenceUrls" text,
    status public.reports_status_enum DEFAULT 'pending'::public.reports_status_enum NOT NULL,
    "handledById" uuid,
    "adminNote" text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "roomId" uuid
);


--
-- Name: room_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_access (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "roomId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "grantType" character varying(16) DEFAULT 'session'::character varying NOT NULL,
    "coinsPaid" integer DEFAULT 0 NOT NULL,
    "grantedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "expiresAt" timestamp with time zone
);


--
-- Name: room_bans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_bans (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "roomId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "bannedById" uuid NOT NULL,
    reason character varying(255),
    "expiresAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_chat_penalties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_chat_penalties (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "roomId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    "strikeCount" integer DEFAULT 0 NOT NULL,
    "chatMutedUntil" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_games; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_games (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "roomId" uuid NOT NULL,
    type public.room_games_type_enum DEFAULT 'tic_tac_toe'::public.room_games_type_enum NOT NULL,
    status public.room_games_status_enum DEFAULT 'waiting'::public.room_games_status_enum NOT NULL,
    "playerXId" uuid,
    "playerOId" uuid,
    board text NOT NULL,
    turn character varying(1) DEFAULT 'X'::character varying NOT NULL,
    winner character varying(8),
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_host_follows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_host_follows (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "followerId" uuid NOT NULL,
    "hostId" uuid NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_moderators; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_moderators (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "roomId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    role public.room_moderators_role_enum DEFAULT 'mod'::public.room_moderators_role_enum NOT NULL,
    "appointedById" uuid NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "canManageMusic" boolean DEFAULT true NOT NULL,
    "canChangeFrames" boolean DEFAULT true NOT NULL,
    "canControlGames" boolean DEFAULT true NOT NULL,
    "canMute" boolean DEFAULT true NOT NULL,
    "canKick" boolean DEFAULT true NOT NULL,
    "canBan" boolean DEFAULT true NOT NULL,
    "canManageSeats" boolean DEFAULT true NOT NULL,
    "canInvite" boolean DEFAULT true NOT NULL,
    "canManageRoom" boolean DEFAULT true NOT NULL
);


--
-- Name: room_music_tracks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_music_tracks (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "uploadedById" uuid NOT NULL,
    url character varying(1024) NOT NULL,
    title character varying(180) NOT NULL,
    artist character varying(180),
    "isActive" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_seat_signals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_seat_signals (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "roomId" uuid NOT NULL,
    "userId" uuid NOT NULL,
    kind character varying(16) NOT NULL,
    "seatIndex" integer,
    "displayName" character varying(128),
    "createdById" uuid,
    "expiresAt" timestamp with time zone NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: room_seats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_seats (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "roomId" uuid NOT NULL,
    "seatIndex" integer NOT NULL,
    "userId" uuid,
    status public.room_seats_status_enum DEFAULT 'empty'::public.room_seats_status_enum NOT NULL,
    "isMuted" boolean DEFAULT false NOT NULL,
    "isHostSeat" boolean DEFAULT false NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "isModeratorMuted" boolean DEFAULT false NOT NULL
);


--
-- Name: rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rooms (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    title character varying(128) NOT NULL,
    description text,
    "coverUrl" character varying(512),
    type public.rooms_type_enum DEFAULT 'voice'::public.rooms_type_enum NOT NULL,
    status public.rooms_status_enum DEFAULT 'open'::public.rooms_status_enum NOT NULL,
    "hostId" uuid NOT NULL,
    "cohostId" uuid,
    "passwordHash" character varying(128),
    "hasPassword" boolean DEFAULT false NOT NULL,
    "seatCount" integer DEFAULT 11 NOT NULL,
    "viewerCount" integer DEFAULT 0 NOT NULL,
    "zegoRoomId" character varying(64),
    topic character varying(64),
    tags text,
    "isPublic" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "accessMode" character varying(16) DEFAULT 'free'::character varying NOT NULL,
    "entryFeeCoins" integer DEFAULT 0 NOT NULL,
    "agencyId" uuid,
    "roomKind" character varying(16) DEFAULT 'standard'::character varying NOT NULL,
    "isPersistent" boolean DEFAULT false NOT NULL,
    "activeHostId" uuid,
    "roomCardUrl" character varying(512),
    "backgroundUrl" character varying(512),
    "musicUrl" character varying(1024),
    "musicTitle" character varying(160),
    "musicArtist" character varying(160),
    "musicStatus" character varying(16) DEFAULT 'stopped'::character varying NOT NULL,
    "musicPositionMs" bigint DEFAULT 0 NOT NULL,
    "musicStartedAt" timestamp with time zone,
    "backgroundEquippedById" uuid,
    "roomCardEquippedById" uuid,
    "liveSessionStartedAt" timestamp with time zone,
    "giftSoundsEnabled" boolean DEFAULT true NOT NULL,
    "chatZoneEnabled" boolean DEFAULT true NOT NULL,
    "charmEnabled" boolean DEFAULT true NOT NULL,
    "bannerEnabled" boolean DEFAULT true NOT NULL,
    "micInteractEnabled" boolean DEFAULT true NOT NULL,
    "entryEffectsEnabled" boolean DEFAULT true NOT NULL,
    "lowGiftEffectsEnabled" boolean DEFAULT true NOT NULL,
    "cupBadgeSeason" character varying(32),
    "emptySince" timestamp with time zone,
    "chatClearedAt" timestamp with time zone,
    "chatAutoClearMinutes" integer DEFAULT 0 NOT NULL
);


--
-- Name: rooms_public_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rooms_public_id_seq
    START WITH 10000000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: slot_game_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.slot_game_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "userId" uuid NOT NULL,
    "gameId" character varying(64) NOT NULL,
    "roomId" uuid,
    code character varying(128) NOT NULL,
    status character varying(16) DEFAULT 'active'::character varying NOT NULL,
    "totalBetCoins" integer DEFAULT 0 NOT NULL,
    "totalWinCoins" integer DEFAULT 0 NOT NULL,
    "spinCount" integer DEFAULT 0 NOT NULL,
    "lastHeartbeatAt" timestamp with time zone,
    "endedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: social_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.social_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "fromUserId" uuid NOT NULL,
    "toUserId" uuid NOT NULL,
    type character varying(32) DEFAULT 'friend'::character varying NOT NULL,
    status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_cosmetics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_cosmetics (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "cosmeticId" uuid NOT NULL,
    equipped boolean DEFAULT false NOT NULL,
    "expiresAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_game_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_game_items (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    sku character varying(64) NOT NULL,
    title character varying(128) NOT NULL,
    "costPoints" integer DEFAULT 0 NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_profiles (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    bio text,
    country character varying(128),
    city character varying(128),
    language character varying(16),
    interests text,
    "coverUrl" character varying(512),
    "followersCount" integer DEFAULT 0 NOT NULL,
    "followingCount" integer DEFAULT 0 NOT NULL,
    "friendsCount" integer DEFAULT 0 NOT NULL,
    "totalReceivedDiamonds" bigint DEFAULT '0'::bigint NOT NULL,
    "totalSentCoins" bigint DEFAULT '0'::bigint NOT NULL,
    "showOnlineStatus" boolean DEFAULT true NOT NULL,
    "allowDmFromStrangers" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "entryEffectUrl" character varying(512),
    "nameColor" character varying(64),
    "albumUrls" text,
    "entryAnimationUrl" character varying(512),
    "roomCardUrl" character varying(512),
    "vipBadgeUrl" character varying(512),
    "levelBadgeUrl" character varying(512),
    "hostBadgeUrl" character varying(512),
    "dmGiftGateEnabled" boolean DEFAULT false NOT NULL,
    "dmRequiredGiftId" uuid,
    "countryChangedAt" timestamp with time zone
);


--
-- Name: user_promo_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_promo_progress (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "monthKey" character varying(7) NOT NULL,
    "usdSpent" numeric(12,2) DEFAULT '0'::numeric NOT NULL,
    "claimedOfferIds" text DEFAULT '[]'::text NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_vips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_vips (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    "vipPlanId" uuid NOT NULL,
    level integer NOT NULL,
    "startsAt" timestamp with time zone NOT NULL,
    "expiresAt" timestamp with time zone NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    email character varying(64),
    phone character varying(32),
    username character varying(48) NOT NULL,
    "passwordHash" character varying(255),
    "displayName" character varying(128) DEFAULT ''::character varying NOT NULL,
    "avatarUrl" character varying(512),
    gender public.users_gender_enum DEFAULT 'unspecified'::public.users_gender_enum NOT NULL,
    birthday date,
    status public.users_status_enum DEFAULT 'active'::public.users_status_enum NOT NULL,
    "isGuest" boolean DEFAULT false NOT NULL,
    "isAdmin" boolean DEFAULT false NOT NULL,
    "emailVerified" boolean DEFAULT false NOT NULL,
    "phoneVerified" boolean DEFAULT false NOT NULL,
    "googleId" character varying(64),
    "facebookId" character varying(64),
    "appleId" character varying(64),
    level integer DEFAULT 1 NOT NULL,
    experience bigint DEFAULT '0'::bigint NOT NULL,
    "refreshTokenHash" character varying(512),
    "lastOnlineAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "nsfwStrikeCount" integer DEFAULT 0 NOT NULL,
    "publicId" character varying(16),
    "genderVerified" boolean DEFAULT false NOT NULL,
    "invitedByUserId" uuid,
    "inviteBoundAt" timestamp with time zone,
    "staffRole" character varying(16),
    "dashboardPermissions" jsonb
);


--
-- Name: vanity_ids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vanity_ids (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    "publicId" character varying(16) NOT NULL,
    status character varying(16) DEFAULT 'available'::character varying NOT NULL,
    "ownerUserId" uuid,
    "priceCoins" integer DEFAULT 0 NOT NULL,
    "reservedUntil" timestamp with time zone,
    "purchasedAt" timestamp with time zone,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "expiresAt" timestamp with time zone,
    "previousPublicId" character varying(32)
);


--
-- Name: vip_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vip_plans (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    level integer NOT NULL,
    name character varying(64) NOT NULL,
    "coinPriceMonthly" integer NOT NULL,
    "badgeUrl" character varying(512),
    benefits text NOT NULL,
    "isActive" boolean DEFAULT true NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: wallet_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallet_transactions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    type public.wallet_transactions_type_enum NOT NULL,
    currency public.wallet_transactions_currency_enum NOT NULL,
    amount bigint NOT NULL,
    "balanceAfter" bigint NOT NULL,
    "referenceType" character varying(255),
    description character varying(255),
    metadata text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "referenceId" character varying(255)
);


--
-- Name: wallets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallets (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    coins bigint DEFAULT '0'::bigint NOT NULL,
    diamonds bigint DEFAULT '0'::bigint NOT NULL,
    "totalRecharged" bigint DEFAULT '0'::bigint NOT NULL,
    "totalWithdrawn" bigint DEFAULT '0'::bigint NOT NULL,
    currency character varying(8) DEFAULT 'USD'::character varying NOT NULL,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "silverCoins" bigint DEFAULT '0'::bigint NOT NULL,
    "gamePoints" bigint DEFAULT '0'::bigint NOT NULL,
    "traderDiamonds" bigint DEFAULT '0'::bigint NOT NULL,
    "agencyDiamonds" bigint DEFAULT '0'::bigint NOT NULL
);


--
-- Name: withdraw_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.withdraw_requests (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    "userId" uuid NOT NULL,
    diamonds bigint NOT NULL,
    "amountFiat" numeric(12,2) NOT NULL,
    currency character varying(8) DEFAULT 'USD'::character varying NOT NULL,
    method character varying(32) NOT NULL,
    "payoutDetails" text NOT NULL,
    status public.withdraw_requests_status_enum DEFAULT 'pending'::public.withdraw_requests_status_enum NOT NULL,
    "reviewedById" uuid,
    "adminNote" text,
    "createdAt" timestamp with time zone DEFAULT now() NOT NULL,
    "updatedAt" timestamp with time zone DEFAULT now() NOT NULL,
    "agentId" uuid
);


--
-- Name: rooms PK_0368a2d7c215f2d0458a54933f2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT "PK_0368a2d7c215f2d0458a54933f2" PRIMARY KEY (id);


--
-- Name: admin_users PK_06744d221bb6145dc61e5dc441d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT "PK_06744d221bb6145dc61e5dc441d" PRIMARY KEY (id);


--
-- Name: contests PK_0b8012f5cf6f444a52179e1227a; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contests
    ADD CONSTRAINT "PK_0b8012f5cf6f444a52179e1227a" PRIMARY KEY (id);


--
-- Name: lucky_box_opens PK_11c0c1026d79ca3c29240b37a17; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_box_opens
    ADD CONSTRAINT "PK_11c0c1026d79ca3c29240b37a17" PRIMARY KEY (id);


--
-- Name: user_profiles PK_1ec6662219f4605723f1e41b6cb; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT "PK_1ec6662219f4605723f1e41b6cb" PRIMARY KEY (id);


--
-- Name: room_host_follows PK_2d0dee47cbf5dc4efc50014881b; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_host_follows
    ADD CONSTRAINT "PK_2d0dee47cbf5dc4efc50014881b" PRIMARY KEY (id);


--
-- Name: recharge_agent_ledger PK_2dcac7d93905be88a37f29a1abe; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agent_ledger
    ADD CONSTRAINT "PK_2dcac7d93905be88a37f29a1abe" PRIMARY KEY (id);


--
-- Name: withdraw_requests PK_2dfe600c271c8d99162ef7dddbf; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withdraw_requests
    ADD CONSTRAINT "PK_2dfe600c271c8d99162ef7dddbf" PRIMARY KEY (id);


--
-- Name: agent_recharges PK_2e4d7dff1129f985eea83e1c712; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_recharges
    ADD CONSTRAINT "PK_2e4d7dff1129f985eea83e1c712" PRIMARY KEY (id);


--
-- Name: recharge_orders PK_3e35ca3c8600b9d9642d7b5f525; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_orders
    ADD CONSTRAINT "PK_3e35ca3c8600b9d9642d7b5f525" PRIMARY KEY (id);


--
-- Name: recharge_agents PK_3fd65b031f25be5bb55a069e304; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agents
    ADD CONSTRAINT "PK_3fd65b031f25be5bb55a069e304" PRIMARY KEY (id);


--
-- Name: chat_messages PK_40c55ee0e571e268b0d3cd37d10; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT "PK_40c55ee0e571e268b0d3cd37d10" PRIMARY KEY (id);


--
-- Name: app_settings PK_4800b266ba790931744b3e53a74; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT "PK_4800b266ba790931744b3e53a74" PRIMARY KEY (id);


--
-- Name: plaza_events PK_50c4f55b973c9aed6d46dcb9da8; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plaza_events
    ADD CONSTRAINT "PK_50c4f55b973c9aed6d46dcb9da8" PRIMARY KEY (id);


--
-- Name: wallet_transactions PK_5120f131bde2cda940ec1a621db; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT "PK_5120f131bde2cda940ec1a621db" PRIMARY KEY (id);


--
-- Name: gifts PK_54242922934e1f322861d116af7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gifts
    ADD CONSTRAINT "PK_54242922934e1f322861d116af7" PRIMARY KEY (id);


--
-- Name: vip_plans PK_557f673f1e6d5a6f9ba2dc1782f; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vip_plans
    ADD CONSTRAINT "PK_557f673f1e6d5a6f9ba2dc1782f" PRIMARY KEY (id);


--
-- Name: recharge_agent_applications PK_571a232e4a09622f7e8a12523a1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agent_applications
    ADD CONSTRAINT "PK_571a232e4a09622f7e8a12523a1" PRIMARY KEY (id);


--
-- Name: ranking_snapshots PK_6253f2250aeb3c754d80821ec06; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ranking_snapshots
    ADD CONSTRAINT "PK_6253f2250aeb3c754d80821ec06" PRIMARY KEY (id);


--
-- Name: user_cosmetics PK_69f95df2183a52e7c06d24d9fb4; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_cosmetics
    ADD CONSTRAINT "PK_69f95df2183a52e7c06d24d9fb4" PRIMARY KEY (id);


--
-- Name: notifications PK_6a72c3c0f683f6462415e653c3a; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT "PK_6a72c3c0f683f6462415e653c3a" PRIMARY KEY (id);


--
-- Name: cosmetics PK_6f1c3811930a2ebd6f7d1a49ad0; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cosmetics
    ADD CONSTRAINT "PK_6f1c3811930a2ebd6f7d1a49ad0" PRIMARY KEY (id);


--
-- Name: drama_series PK_6ff9d2807bc6d716adb7a775cfe; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drama_series
    ADD CONSTRAINT "PK_6ff9d2807bc6d716adb7a775cfe" PRIMARY KEY (id);


--
-- Name: payment_webhook_events PK_750875e71d97974be92cee813ba; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_webhook_events
    ADD CONSTRAINT "PK_750875e71d97974be92cee813ba" PRIMARY KEY (id);


--
-- Name: room_access PK_8120c22097d247ea41f4f0df7d5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_access
    ADD CONSTRAINT "PK_8120c22097d247ea41f4f0df7d5" PRIMARY KEY (id);


--
-- Name: blocks PK_8244fa1495c4e9222a01059244b; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT "PK_8244fa1495c4e9222a01059244b" PRIMARY KEY (id);


--
-- Name: wallets PK_8402e5df5a30a229380e83e4f7e; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallets
    ADD CONSTRAINT "PK_8402e5df5a30a229380e83e4f7e" PRIMARY KEY (id);


--
-- Name: agency_members PK_868eae9a18f43f0a9a810bfe981; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_members
    ADD CONSTRAINT "PK_868eae9a18f43f0a9a810bfe981" PRIMARY KEY (id);


--
-- Name: gift_sends PK_86f0103cb304318f377b7395694; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_sends
    ADD CONSTRAINT "PK_86f0103cb304318f377b7395694" PRIMARY KEY (id);


--
-- Name: follows PK_8988f607744e16ff79da3b8a627; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT "PK_8988f607744e16ff79da3b8a627" PRIMARY KEY (id);


--
-- Name: host_monthly_progress PK_8aa4f101d579ad979edc800e73c; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host_monthly_progress
    ADD CONSTRAINT "PK_8aa4f101d579ad979edc800e73c" PRIMARY KEY (id);


--
-- Name: agencies PK_8ab1f1f53f56c8255b0d7e68b28; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agencies
    ADD CONSTRAINT "PK_8ab1f1f53f56c8255b0d7e68b28" PRIMARY KEY (id);


--
-- Name: female_identity_verifications PK_8b4687c9d3fb7155f2af4bf919e; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.female_identity_verifications
    ADD CONSTRAINT "PK_8b4687c9d3fb7155f2af4bf919e" PRIMARY KEY (id);


--
-- Name: contest_entries PK_94074f3f5cc3ee7b5763a6041b1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contest_entries
    ADD CONSTRAINT "PK_94074f3f5cc3ee7b5763a6041b1" PRIMARY KEY (id);


--
-- Name: otp_codes PK_9d0487965ac1837d57fec4d6a26; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_codes
    ADD CONSTRAINT "PK_9d0487965ac1837d57fec4d6a26" PRIMARY KEY (id);


--
-- Name: drama_episodes PK_a031dfde0ceeb2b3b3dad9398c8; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drama_episodes
    ADD CONSTRAINT "PK_a031dfde0ceeb2b3b3dad9398c8" PRIMARY KEY (id);


--
-- Name: users PK_a3ffb1c0c8416b9fc6f907b7433; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT "PK_a3ffb1c0c8416b9fc6f907b7433" PRIMARY KEY (id);


--
-- Name: user_game_items PK_a4be1b76282d18a3eadd1e41d7f; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_game_items
    ADD CONSTRAINT "PK_a4be1b76282d18a3eadd1e41d7f" PRIMARY KEY (id);


--
-- Name: abuse_logs PK_aa2916720a09f5d9c716da05bff; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abuse_logs
    ADD CONSTRAINT "PK_aa2916720a09f5d9c716da05bff" PRIMARY KEY (id);


--
-- Name: drama_reactions PK_ac087a86f783bc8d873220da0a3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drama_reactions
    ADD CONSTRAINT "PK_ac087a86f783bc8d873220da0a3" PRIMARY KEY (id);


--
-- Name: devices PK_b1514758245c12daf43486dd1f0; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT "PK_b1514758245c12daf43486dd1f0" PRIMARY KEY (id);


--
-- Name: room_chat_penalties PK_c2151e64c2dbe9fb92022d8bf64; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_chat_penalties
    ADD CONSTRAINT "PK_c2151e64c2dbe9fb92022d8bf64" PRIMARY KEY (id);


--
-- Name: plaza_event_subscriptions PK_c3482dc3b92266719eb2a64a01c; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plaza_event_subscriptions
    ADD CONSTRAINT "PK_c3482dc3b92266719eb2a64a01c" PRIMARY KEY (id);


--
-- Name: user_vips PK_c84a4422f457962181145c047b1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_vips
    ADD CONSTRAINT "PK_c84a4422f457962181145c047b1" PRIMARY KEY (id);


--
-- Name: lucky_reward_grants PK_cc275bfe953b2e866f52d50752f; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_reward_grants
    ADD CONSTRAINT "PK_cc275bfe953b2e866f52d50752f" PRIMARY KEY (id);


--
-- Name: reports PK_d9013193989303580053c0b5ef6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT "PK_d9013193989303580053c0b5ef6" PRIMARY KEY (id);


--
-- Name: room_moderators PK_eb5311c5c35ab7b7e8a94662418; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_moderators
    ADD CONSTRAINT "PK_eb5311c5c35ab7b7e8a94662418" PRIMARY KEY (id);


--
-- Name: user_promo_progress PK_eb66d6a1fd6e8a6109765569ab6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_promo_progress
    ADD CONSTRAINT "PK_eb66d6a1fd6e8a6109765569ab6" PRIMARY KEY (id);


--
-- Name: chat_participants PK_ebf68c52a2b4dceb777672b782d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_participants
    ADD CONSTRAINT "PK_ebf68c52a2b4dceb777672b782d" PRIMARY KEY (id);


--
-- Name: lucky_boxes PK_f28f77ccf0cd86ded8993a83ecd; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_boxes
    ADD CONSTRAINT "PK_f28f77ccf0cd86ded8993a83ecd" PRIMARY KEY (id);


--
-- Name: room_games PK_f4ccf210273a62f07cdcdd75d0d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_games
    ADD CONSTRAINT "PK_f4ccf210273a62f07cdcdd75d0d" PRIMARY KEY (id);


--
-- Name: room_seats PK_f7eb712e647c078989f5d82485d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seats
    ADD CONSTRAINT "PK_f7eb712e647c078989f5d82485d" PRIMARY KEY (id);


--
-- Name: room_bans PK_f9d925f0f4d6ce338e967a299e0; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_bans
    ADD CONSTRAINT "PK_f9d925f0f4d6ce338e967a299e0" PRIMARY KEY (id);


--
-- Name: chat_conversations PK_ff117d9f57807c4f2e3034a39f3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_conversations
    ADD CONSTRAINT "PK_ff117d9f57807c4f2e3034a39f3" PRIMARY KEY (id);


--
-- Name: lucky_reward_grants REL_9519ebc45afbce61f299349a40; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_reward_grants
    ADD CONSTRAINT "REL_9519ebc45afbce61f299349a40" UNIQUE ("openId");


--
-- Name: follows UQ_105079775692df1f8799ed0fac8; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT "UQ_105079775692df1f8799ed0fac8" UNIQUE ("followerId", "followingId");


--
-- Name: profile_visits UQ_1dfab9da5d08380dc9515561046; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_visits
    ADD CONSTRAINT "UQ_1dfab9da5d08380dc9515561046" UNIQUE ("visitorId", "profileUserId");


--
-- Name: wallets UQ_2ecdb33f23e9a6fc392025c0b97; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallets
    ADD CONSTRAINT "UQ_2ecdb33f23e9a6fc392025c0b97" UNIQUE ("userId");


--
-- Name: blocks UQ_4abe7bad89347a663fc8f428d0d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT "UQ_4abe7bad89347a663fc8f428d0d" UNIQUE ("blockerId", "blockedId");


--
-- Name: plaza_event_subscriptions UQ_51935bd821dbdfc63b928a17542; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plaza_event_subscriptions
    ADD CONSTRAINT "UQ_51935bd821dbdfc63b928a17542" UNIQUE ("eventId", "userId");


--
-- Name: room_seat_signals UQ_5679d603a3f0b0e78181a4af2b4; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seat_signals
    ADD CONSTRAINT "UQ_5679d603a3f0b0e78181a4af2b4" UNIQUE ("roomId", "userId", kind);


--
-- Name: host_monthly_progress UQ_7426b29b4943552abef40db8b59; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host_monthly_progress
    ADD CONSTRAINT "UQ_7426b29b4943552abef40db8b59" UNIQUE ("userId", "yearMonth");


--
-- Name: room_host_follows UQ_7977426f9da5506c39871c2a5f8; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_host_follows
    ADD CONSTRAINT "UQ_7977426f9da5506c39871c2a5f8" UNIQUE ("followerId", "hostId");


--
-- Name: chat_participants UQ_79a34cee1c3ef6075996fda91b6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_participants
    ADD CONSTRAINT "UQ_79a34cee1c3ef6075996fda91b6" UNIQUE ("conversationId", "userId");


--
-- Name: agency_members UQ_7a8c159d139129a857092199978; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_members
    ADD CONSTRAINT "UQ_7a8c159d139129a857092199978" UNIQUE ("agencyId", "userId");


--
-- Name: room_moderators UQ_7bcfcdf6f7ceef54c392600fa42; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_moderators
    ADD CONSTRAINT "UQ_7bcfcdf6f7ceef54c392600fa42" UNIQUE ("roomId", "userId");


--
-- Name: user_profiles UQ_8481388d6325e752cd4d7e26c6d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT "UQ_8481388d6325e752cd4d7e26c6d" UNIQUE ("userId");


--
-- Name: social_requests UQ_93ea00bdb5eec405388cd6dce8e; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_requests
    ADD CONSTRAINT "UQ_93ea00bdb5eec405388cd6dce8e" UNIQUE ("fromUserId", "toUserId", type);


--
-- Name: user_game_items UQ_9baa7a5490b5cac05e1050115dd; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_game_items
    ADD CONSTRAINT "UQ_9baa7a5490b5cac05e1050115dd" UNIQUE ("userId", sku);


--
-- Name: room_seats UQ_acd51477b62103813b2cf6d1342; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seats
    ADD CONSTRAINT "UQ_acd51477b62103813b2cf6d1342" UNIQUE ("roomId", "seatIndex");


--
-- Name: agent_recharges UQ_agent_recharge_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_recharges
    ADD CONSTRAINT "UQ_agent_recharge_idempotency" UNIQUE ("idempotencyKey");


--
-- Name: drama_reactions UQ_b250958e8591404be077b7aebd7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drama_reactions
    ADD CONSTRAINT "UQ_b250958e8591404be077b7aebd7" UNIQUE ("userId", "targetType", "targetId");


--
-- Name: room_bans UQ_c92968d50abe6ed1d9a3364e5f1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_bans
    ADD CONSTRAINT "UQ_c92968d50abe6ed1d9a3364e5f1" UNIQUE ("roomId", "userId");


--
-- Name: contest_entries UQ_d2f5c8524f52e9818c65a41e543; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contest_entries
    ADD CONSTRAINT "UQ_d2f5c8524f52e9818c65a41e543" UNIQUE ("contestId", "userId");


--
-- Name: payment_webhook_events UQ_payment_webhook_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_webhook_events
    ADD CONSTRAINT "UQ_payment_webhook_event" UNIQUE (provider, "eventId");


--
-- Name: recharge_agents UQ_recharge_agent_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agents
    ADD CONSTRAINT "UQ_recharge_agent_user" UNIQUE ("userId");


--
-- Name: recharge_orders UQ_recharge_provider_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_orders
    ADD CONSTRAINT "UQ_recharge_provider_order" UNIQUE (provider, "providerOrderId");


--
-- Name: recharge_orders UQ_recharge_provider_payment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_orders
    ADD CONSTRAINT "UQ_recharge_provider_payment" UNIQUE (provider, "providerPaymentId");


--
-- Name: agency_applications agency_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_applications
    ADD CONSTRAINT agency_applications_pkey PRIMARY KEY (id);


--
-- Name: agency_follows agency_follows_agencyId_userId_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_follows
    ADD CONSTRAINT "agency_follows_agencyId_userId_key" UNIQUE ("agencyId", "userId");


--
-- Name: agency_follows agency_follows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_follows
    ADD CONSTRAINT agency_follows_pkey PRIMARY KEY (id);


--
-- Name: gift_categories gift_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_categories
    ADD CONSTRAINT gift_categories_pkey PRIMARY KEY (id);


--
-- Name: host_target_claims host_target_claims_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host_target_claims
    ADD CONSTRAINT host_target_claims_pkey PRIMARY KEY (id);


--
-- Name: profile_visits profile_visits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_visits
    ADD CONSTRAINT profile_visits_pkey PRIMARY KEY (id);


--
-- Name: recharge_agent_contacts recharge_agent_contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agent_contacts
    ADD CONSTRAINT recharge_agent_contacts_pkey PRIMARY KEY (id);


--
-- Name: room_music_tracks room_music_tracks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_music_tracks
    ADD CONSTRAINT room_music_tracks_pkey PRIMARY KEY (id);


--
-- Name: room_seat_signals room_seat_signals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seat_signals
    ADD CONSTRAINT room_seat_signals_pkey PRIMARY KEY (id);


--
-- Name: slot_game_sessions slot_game_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.slot_game_sessions
    ADD CONSTRAINT slot_game_sessions_pkey PRIMARY KEY (id);


--
-- Name: social_requests social_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_requests
    ADD CONSTRAINT social_requests_pkey PRIMARY KEY (id);


--
-- Name: host_target_claims uq_host_target_claim; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host_target_claims
    ADD CONSTRAINT uq_host_target_claim UNIQUE ("userId", "yearMonth", "stageIndex");


--
-- Name: vanity_ids vanity_ids_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vanity_ids
    ADD CONSTRAINT vanity_ids_pkey PRIMARY KEY (id);


--
-- Name: IDX_017e195ed5231583b12a25a335; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_017e195ed5231583b12a25a335" ON public.room_access USING btree ("roomId");


--
-- Name: IDX_03b0333dc1339155fe9ca667b8; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_03b0333dc1339155fe9ca667b8" ON public.room_chat_penalties USING btree ("roomId", "userId");


--
-- Name: IDX_0e6521dead3cae78f5dfb99619; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_0e6521dead3cae78f5dfb99619" ON public.gift_sends USING btree ("receiverId");


--
-- Name: IDX_0ff026204b1ec2fe6e94ad89c1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_0ff026204b1ec2fe6e94ad89c1" ON public.user_promo_progress USING btree ("userId", "monthKey");


--
-- Name: IDX_1083039a84464289149324b7a6; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_1083039a84464289149324b7a6" ON public.room_seats USING btree ("roomId", "userId") WHERE ("userId" IS NOT NULL);


--
-- Name: IDX_14cf5cb9c3da401f4981574408; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_14cf5cb9c3da401f4981574408" ON public.room_host_follows USING btree ("hostId");


--
-- Name: IDX_1790f7a8dda278d15599817955; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_1790f7a8dda278d15599817955" ON public.plaza_event_subscriptions USING btree ("eventId");


--
-- Name: IDX_18afd82880c01dfd863cce86ce; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_18afd82880c01dfd863cce86ce" ON public.contest_entries USING btree ("contestId");


--
-- Name: IDX_18b4704722f74a4b3dc0ca354a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_18b4704722f74a4b3dc0ca354a" ON public.plaza_events USING btree ("hostId");


--
-- Name: IDX_19ecab949371747d82ce365d6d; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_19ecab949371747d82ce365d6d" ON public.agency_members USING btree ("agencyId");


--
-- Name: IDX_1cca697c378ba7661c5e79dc22; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_1cca697c378ba7661c5e79dc22" ON public.users USING btree ("invitedByUserId");


--
-- Name: IDX_1e41a88dbde6094d3c6ebe5444; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_1e41a88dbde6094d3c6ebe5444" ON public.agency_applications USING btree ("applicantId");


--
-- Name: IDX_1ea16c73ecef4bab2f61c31c88; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_1ea16c73ecef4bab2f61c31c88" ON public.agencies USING btree (name);


--
-- Name: IDX_1ea1ca7526faba4878f91d4a3d; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_1ea1ca7526faba4878f91d4a3d" ON public.withdraw_requests USING btree ("agentId");


--
-- Name: IDX_1f792acf882931dd013fff0dab; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_1f792acf882931dd013fff0dab" ON public.drama_episodes USING btree ("isPublished");


--
-- Name: IDX_22b88b48be38104518c775a1fb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_22b88b48be38104518c775a1fb" ON public.room_music_tracks USING btree ("uploadedById");


--
-- Name: IDX_24051aebfe891219eaca0c1b76; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_24051aebfe891219eaca0c1b76" ON public.otp_codes USING btree (target);


--
-- Name: IDX_2494dde7fc5445fe3117177a39; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_2494dde7fc5445fe3117177a39" ON public.plaza_events USING btree ("roomId");


--
-- Name: IDX_2873882c38e8c07d98cb64f962; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_2873882c38e8c07d98cb64f962" ON public.admin_users USING btree (username);


--
-- Name: IDX_291baf6ac9479a2645852d114a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_291baf6ac9479a2645852d114a" ON public.social_requests USING btree ("fromUserId");


--
-- Name: IDX_29cb146a91ac8d510be26479aa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_29cb146a91ac8d510be26479aa" ON public.drama_series USING btree ("isPublished");


--
-- Name: IDX_2d3f0315552537f47e08be8fc4; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_2d3f0315552537f47e08be8fc4" ON public.cosmetics USING btree (code);


--
-- Name: IDX_32be8e6bd24d59149ebc4b29f2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_32be8e6bd24d59149ebc4b29f2" ON public.social_requests USING btree ("toUserId");


--
-- Name: IDX_33f0416f1aa1aa1976046d27ec; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_33f0416f1aa1aa1976046d27ec" ON public.recharge_agent_applications USING btree ("userId");


--
-- Name: IDX_3a02b8ce593ee323902ae1403d; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_3a02b8ce593ee323902ae1403d" ON public.room_access USING btree ("roomId", "userId");


--
-- Name: IDX_3b4e13a1d9b6878a602b486ef7; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_3b4e13a1d9b6878a602b486ef7" ON public.cosmetics USING btree (type);


--
-- Name: IDX_41d42c9f76df6bacf81bdc16e5; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_41d42c9f76df6bacf81bdc16e5" ON public.profile_visits USING btree ("visitorId");


--
-- Name: IDX_431a010b81af0807f39bd11c84; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_431a010b81af0807f39bd11c84" ON public.room_chat_penalties USING btree ("chatMutedUntil");


--
-- Name: IDX_4353be8309ce86650def2f8572; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_4353be8309ce86650def2f8572" ON public.reports USING btree ("reporterId");


--
-- Name: IDX_45745953065384cc9c4264c2a3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_45745953065384cc9c4264c2a3" ON public.chat_messages USING btree ("conversationId");


--
-- Name: IDX_45aef1da935b11cdf48a1adacc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_45aef1da935b11cdf48a1adacc" ON public.plaza_event_subscriptions USING btree ("userId");


--
-- Name: IDX_46dc0b73131f2f646709f1b6ad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_46dc0b73131f2f646709f1b6ad" ON public.ranking_snapshots USING btree (period, category, "periodKey");


--
-- Name: IDX_4840b2168b71cec76465b70390; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_4840b2168b71cec76465b70390" ON public.drama_reactions USING btree ("targetId");


--
-- Name: IDX_4ab3460ba2091fc0fbf66d6650; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_4ab3460ba2091fc0fbf66d6650" ON public.rooms USING btree (title);


--
-- Name: IDX_4ddcc53b3247c634c352603dc0; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_4ddcc53b3247c634c352603dc0" ON public.room_bans USING btree ("roomId");


--
-- Name: IDX_4e6b1097a3e2f1cfa1eae0ea49; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_4e6b1097a3e2f1cfa1eae0ea49" ON public.vanity_ids USING btree ("publicId");


--
-- Name: IDX_599c2d45b92f3aca0cbb687a86; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_599c2d45b92f3aca0cbb687a86" ON public.contests USING btree (status);


--
-- Name: IDX_5e08ad421ea4742ab0b892130f; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_5e08ad421ea4742ab0b892130f" ON public.payment_webhook_events USING btree (provider);


--
-- Name: IDX_5e82de8927486c513b9cc14374; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_5e82de8927486c513b9cc14374" ON public.room_chat_penalties USING btree ("userId");


--
-- Name: IDX_5f8501202b13cd3f35e2929e01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_5f8501202b13cd3f35e2929e01" ON public.recharge_orders USING btree ("userId");


--
-- Name: IDX_692a909ee0fa9383e7859f9b40; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_692a909ee0fa9383e7859f9b40" ON public.notifications USING btree ("userId");


--
-- Name: IDX_69454773f1e666a14c6a953935; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_69454773f1e666a14c6a953935" ON public.wallet_transactions USING btree ("userId");


--
-- Name: IDX_6c939085068c5539bad393be6b; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_6c939085068c5539bad393be6b" ON public.rooms USING btree ("hostId");


--
-- Name: IDX_6e2c115bfdba758d450a5dc684; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_6e2c115bfdba758d450a5dc684" ON public.room_moderators USING btree ("userId");


--
-- Name: IDX_70fe713ef5463e7aa66bbcb321; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_70fe713ef5463e7aa66bbcb321" ON public.gift_sends USING btree ("giftId");


--
-- Name: IDX_75e48a17f75ab2cb3104254145; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_75e48a17f75ab2cb3104254145" ON public.gifts USING btree ("brandAgencyId");


--
-- Name: IDX_773d65d80dbc2f556c2b8b5804; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_773d65d80dbc2f556c2b8b5804" ON public.slot_game_sessions USING btree ("userId");


--
-- Name: IDX_776c4d82b2a8bdf328bc3f51cc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_776c4d82b2a8bdf328bc3f51cc" ON public.user_vips USING btree ("userId");


--
-- Name: IDX_7ac096a8bfe8bad3810cf3b01b; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_7ac096a8bfe8bad3810cf3b01b" ON public.user_cosmetics USING btree ("userId");


--
-- Name: IDX_7b92071328fdd5212b978ad248; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_7b92071328fdd5212b978ad248" ON public.gifts USING btree (name);


--
-- Name: IDX_7d6d35ff9fc2fd3f8ef1a47fe1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_7d6d35ff9fc2fd3f8ef1a47fe1" ON public.agencies USING btree ("activationCode");


--
-- Name: IDX_7e7cbcfb689216ad5d9700657b; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_7e7cbcfb689216ad5d9700657b" ON public.room_chat_penalties USING btree ("roomId");


--
-- Name: IDX_813db5e5b2b63149563d601d27; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_813db5e5b2b63149563d601d27" ON public.agencies USING btree ("publicId");


--
-- Name: IDX_8256f8fefaf42f4fdb204f58f4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_8256f8fefaf42f4fdb204f58f4" ON public.room_seats USING btree ("roomId");


--
-- Name: IDX_87fdb064cf16b6ae164eb4862c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_87fdb064cf16b6ae164eb4862c" ON public.host_monthly_progress USING btree ("yearMonth");


--
-- Name: IDX_891986e15af0e6c9936d3d25f9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_891986e15af0e6c9936d3d25f9" ON public.recharge_agents USING btree ("userId");


--
-- Name: IDX_8abdc3af0797198cc1edb6de94; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_8abdc3af0797198cc1edb6de94" ON public.profile_visits USING btree ("profileUserId");


--
-- Name: IDX_8b5199ab654843357766592c0d; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_8b5199ab654843357766592c0d" ON public.recharge_agent_ledger USING btree ("agentId");


--
-- Name: IDX_8effac5e5a0f87cc71753d98e2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_8effac5e5a0f87cc71753d98e2" ON public.lucky_box_opens USING btree ("userId");


--
-- Name: IDX_8ff32f632e2a009c3b4a2c9306; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_8ff32f632e2a009c3b4a2c9306" ON public.host_monthly_progress USING btree ("userId");


--
-- Name: IDX_9099c98f00a1b5aca6b8f7f04a; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_9099c98f00a1b5aca6b8f7f04a" ON public.users USING btree ("publicId");


--
-- Name: IDX_947420beea7fcaae511e353fdb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_947420beea7fcaae511e353fdb" ON public.contests USING btree ("roomId");


--
-- Name: IDX_9519ebc45afbce61f299349a40; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_9519ebc45afbce61f299349a40" ON public.lucky_reward_grants USING btree ("openId");


--
-- Name: IDX_95389d5c14b7fb1ba4cd7f8405; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_95389d5c14b7fb1ba4cd7f8405" ON public.contests USING btree ("agencyId");


--
-- Name: IDX_96c1bb2ccf043af338008825a7; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_96c1bb2ccf043af338008825a7" ON public.user_cosmetics USING btree ("cosmeticId");


--
-- Name: IDX_96fefd8880f769fe803a9dca7c; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_96fefd8880f769fe803a9dca7c" ON public.vip_plans USING btree (level);


--
-- Name: IDX_975c2db59c65c05fd9c6b63a2a; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_975c2db59c65c05fd9c6b63a2a" ON public.app_settings USING btree (key);


--
-- Name: IDX_97672ac88f789774dd47f7c8be; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_97672ac88f789774dd47f7c8be" ON public.users USING btree (email);


--
-- Name: IDX_9997ca24e7f706f9d864ab36e2; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_9997ca24e7f706f9d864ab36e2" ON public.room_music_tracks USING btree (url);


--
-- Name: IDX_a000cca60bcf04454e72769949; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_a000cca60bcf04454e72769949" ON public.users USING btree (phone);


--
-- Name: IDX_a2f99b56f95944955ff3ff26c4; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_a2f99b56f95944955ff3ff26c4" ON public.recharge_agent_applications USING btree ("paymentReference");


--
-- Name: IDX_a4297dabb6418274a1591c5592; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_a4297dabb6418274a1591c5592" ON public.agency_applications USING btree (status);


--
-- Name: IDX_a45d7aca30d67970c093fa4eed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_a45d7aca30d67970c093fa4eed" ON public.room_access USING btree ("userId");


--
-- Name: IDX_a64905cd550435773e95786a31; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_a64905cd550435773e95786a31" ON public.gift_categories USING btree (key);


--
-- Name: IDX_a6bb192fadb7310808004f5753; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_a6bb192fadb7310808004f5753" ON public.agency_members USING btree ("userId");


--
-- Name: IDX_a8f03d80e42da1162e25591dc4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_a8f03d80e42da1162e25591dc4" ON public.withdraw_requests USING btree ("userId");


--
-- Name: IDX_agencies_publicId; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_agencies_publicId" ON public.agencies USING btree ("publicId") WHERE ("publicId" IS NOT NULL);


--
-- Name: IDX_agency_follows_agencyId; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_agency_follows_agencyId" ON public.agency_follows USING btree ("agencyId");


--
-- Name: IDX_agency_follows_userId; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_agency_follows_userId" ON public.agency_follows USING btree ("userId");


--
-- Name: IDX_b11f59fb7752d74ef9df35d090; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_b11f59fb7752d74ef9df35d090" ON public.drama_series USING btree (category);


--
-- Name: IDX_b35f7142e39a576ae2aa83764e; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_b35f7142e39a576ae2aa83764e" ON public.plaza_events USING btree (status);


--
-- Name: IDX_b412281aebf17aeeff410e4579; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_b412281aebf17aeeff410e4579" ON public.room_moderators USING btree ("roomId");


--
-- Name: IDX_b7e5d981a2a0843c6a5721dc12; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_b7e5d981a2a0843c6a5721dc12" ON public.recharge_agent_contacts USING btree (country);


--
-- Name: IDX_b87401da081a7153ec3bd1e6ab; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_b87401da081a7153ec3bd1e6ab" ON public.blocks USING btree ("blockedId");


--
-- Name: IDX_bbd9b86138c398837390bb2167; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_bbd9b86138c398837390bb2167" ON public.drama_episodes USING btree ("seriesId");


--
-- Name: IDX_bd3a636f5eb88d1273956ebcd9; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_bd3a636f5eb88d1273956ebcd9" ON public.room_games USING btree ("roomId");


--
-- Name: IDX_c297837543a492e58f3fed7982; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_c297837543a492e58f3fed7982" ON public.chat_conversations USING btree ("directKey");


--
-- Name: IDX_c64f80e2e15b21549e03afcb53; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_c64f80e2e15b21549e03afcb53" ON public.reports USING btree ("roomId");


--
-- Name: IDX_c816c4cce8f709165187b6fb2e; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_c816c4cce8f709165187b6fb2e" ON public.room_bans USING btree ("userId");


--
-- Name: IDX_caed89ce0fe60c7c459c2a8332; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_caed89ce0fe60c7c459c2a8332" ON public.lucky_boxes USING btree (code);


--
-- Name: IDX_cd7faa31851a2153125fd1af73; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_cd7faa31851a2153125fd1af73" ON public.reports USING btree ("targetId");


--
-- Name: IDX_ceb65dda05e249f63954010ef5; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ceb65dda05e249f63954010ef5" ON public.abuse_logs USING btree ("userId");


--
-- Name: IDX_d05e3de1eb08a867f84224ec64; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_d05e3de1eb08a867f84224ec64" ON public.contest_entries USING btree ("userId");


--
-- Name: IDX_d7fa6127b9d1676d65c1785f7d; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_d7fa6127b9d1676d65c1785f7d" ON public.rooms USING btree ("agencyId");


--
-- Name: IDX_d877012f96fd086d5a929911bc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_d877012f96fd086d5a929911bc" ON public.room_music_tracks USING btree ("isActive");


--
-- Name: IDX_dcd0c8a4b10af9c986e510b9ec; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_dcd0c8a4b10af9c986e510b9ec" ON public.admin_users USING btree (email);


--
-- Name: IDX_ddd8591ce98b8751ed56795d9c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ddd8591ce98b8751ed56795d9c" ON public.slot_game_sessions USING btree ("roomId");


--
-- Name: IDX_df653e18001df19542aa441014; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_df653e18001df19542aa441014" ON public.female_identity_verifications USING btree ("userId");


--
-- Name: IDX_dfd1b05e9d1ad68adf710c1ec3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_dfd1b05e9d1ad68adf710c1ec3" ON public.slot_game_sessions USING btree ("gameId");


--
-- Name: IDX_e761540486cbe026a6ac094208; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_e761540486cbe026a6ac094208" ON public.host_target_claims USING btree ("userId");


--
-- Name: IDX_e8a5d59f0ac3040395f159507c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_e8a5d59f0ac3040395f159507c" ON public.devices USING btree ("userId");


--
-- Name: IDX_e91dc58a06bb3ef1e4c530d905; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_e91dc58a06bb3ef1e4c530d905" ON public.agent_recharges USING btree ("recipientUserId");


--
-- Name: IDX_e9e5dd67910dbc65a3a6fbc155; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_e9e5dd67910dbc65a3a6fbc155" ON public.user_game_items USING btree ("userId");


--
-- Name: IDX_ea41e82e13c644166d9ea469fe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ea41e82e13c644166d9ea469fe" ON public.room_seat_signals USING btree ("roomId");


--
-- Name: IDX_ed8a33b2c1ac10922c2354f5cf; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ed8a33b2c1ac10922c2354f5cf" ON public.blocks USING btree ("blockerId");


--
-- Name: IDX_ef463dd9a2ce0d673350e36e0f; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ef463dd9a2ce0d673350e36e0f" ON public.follows USING btree ("followingId");


--
-- Name: IDX_ef8e7397bbb9ce5bc09df5413d; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ef8e7397bbb9ce5bc09df5413d" ON public.user_game_items USING btree (sku);


--
-- Name: IDX_eff4bc5b2d97dc474531fb357a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_eff4bc5b2d97dc474531fb357a" ON public.drama_reactions USING btree ("userId");


--
-- Name: IDX_f09cdd61f21d144c689b8acbdb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_f09cdd61f21d144c689b8acbdb" ON public.lucky_box_opens USING btree ("boxId");


--
-- Name: IDX_f1f8d72ccc44a6da7b7a2e8f77; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_f1f8d72ccc44a6da7b7a2e8f77" ON public.agencies USING btree ("ownerId");


--
-- Name: IDX_f6fdeaefc5d27901a8ad3d7c48; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_f6fdeaefc5d27901a8ad3d7c48" ON public.room_access USING btree ("expiresAt");


--
-- Name: IDX_f82f10254ad10ef1e6de447004; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_f82f10254ad10ef1e6de447004" ON public.agent_recharges USING btree ("agentId");


--
-- Name: IDX_f9f7aa11e20e27f32fc6456551; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_f9f7aa11e20e27f32fc6456551" ON public.gift_sends USING btree ("senderId");


--
-- Name: IDX_fa0749b418feb14a7f2757994a; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_fa0749b418feb14a7f2757994a" ON public.room_seat_signals USING btree ("userId");


--
-- Name: IDX_fb6add83b1a7acc94433d38569; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_fb6add83b1a7acc94433d38569" ON public.chat_participants USING btree ("userId");


--
-- Name: IDX_fc6b58e41e9a871dacbe9077de; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_fc6b58e41e9a871dacbe9077de" ON public.chat_messages USING btree ("senderId");


--
-- Name: IDX_fdb91868b03a2040db408a5333; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_fdb91868b03a2040db408a5333" ON public.follows USING btree ("followerId");


--
-- Name: IDX_fe0bb3f6520ee0469504521e71; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_fe0bb3f6520ee0469504521e71" ON public.users USING btree (username);


--
-- Name: IDX_fe7931b7501576746f27f25d50; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_fe7931b7501576746f27f25d50" ON public.room_seat_signals USING btree (kind);


--
-- Name: IDX_febd82d58a786b258d29c20952; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_febd82d58a786b258d29c20952" ON public.room_host_follows USING btree ("followerId");


--
-- Name: IDX_ffa48c8c78e4c4d0cb29bd6d12; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_ffa48c8c78e4c4d0cb29bd6d12" ON public.chat_participants USING btree ("conversationId");


--
-- Name: IDX_gifts_brandAgencyId; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_gifts_brandAgencyId" ON public.gifts USING btree ("brandAgencyId") WHERE ("brandAgencyId" IS NOT NULL);


--
-- Name: IDX_lucky_open_user_box_day; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IDX_lucky_open_user_box_day" ON public.lucky_box_opens USING btree ("userId", "boxId", "dayKey");


--
-- Name: IDX_room_chat_penalties_room_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX "IDX_room_chat_penalties_room_user" ON public.room_chat_penalties USING btree ("roomId", "userId");


--
-- Name: idx_users_public_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_users_public_id ON public.users USING btree ("publicId");


--
-- Name: idx_withdraw_requests_agent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_withdraw_requests_agent ON public.withdraw_requests USING btree ("agentId");


--
-- Name: uq_agencies_name_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_agencies_name_ci ON public.agencies USING btree (lower((name)::text));


--
-- Name: uq_room_agency_host; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_room_agency_host ON public.rooms USING btree ("agencyId", "hostId") WHERE ("agencyId" IS NOT NULL);


--
-- Name: uq_wallet_tx_user_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_wallet_tx_user_reference ON public.wallet_transactions USING btree ("userId", "referenceType", "referenceId") WHERE (("referenceType" IS NOT NULL) AND ("referenceId" IS NOT NULL));


--
-- Name: gift_sends FK_0e6521dead3cae78f5dfb996194; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_sends
    ADD CONSTRAINT "FK_0e6521dead3cae78f5dfb996194" FOREIGN KEY ("receiverId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_host_follows FK_14cf5cb9c3da401f4981574408e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_host_follows
    ADD CONSTRAINT "FK_14cf5cb9c3da401f4981574408e" FOREIGN KEY ("hostId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: chat_messages FK_17a42899ce72cd55dc25fb33139; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT "FK_17a42899ce72cd55dc25fb33139" FOREIGN KEY ("replyToId") REFERENCES public.chat_messages(id) ON DELETE SET NULL;


--
-- Name: agency_members FK_19ecab949371747d82ce365d6df; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_members
    ADD CONSTRAINT "FK_19ecab949371747d82ce365d6df" FOREIGN KEY ("agencyId") REFERENCES public.agencies(id) ON DELETE CASCADE;


--
-- Name: room_games FK_1bef6f4a4b8fa3b7abf26042b47; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_games
    ADD CONSTRAINT "FK_1bef6f4a4b8fa3b7abf26042b47" FOREIGN KEY ("playerXId") REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: agency_applications FK_1e41a88dbde6094d3c6ebe54441; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_applications
    ADD CONSTRAINT "FK_1e41a88dbde6094d3c6ebe54441" FOREIGN KEY ("applicantId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_music_tracks FK_22b88b48be38104518c775a1fbd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_music_tracks
    ADD CONSTRAINT "FK_22b88b48be38104518c775a1fbd" FOREIGN KEY ("uploadedById") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: social_requests FK_291baf6ac9479a2645852d114a6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_requests
    ADD CONSTRAINT "FK_291baf6ac9479a2645852d114a6" FOREIGN KEY ("fromUserId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: wallets FK_2ecdb33f23e9a6fc392025c0b97; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallets
    ADD CONSTRAINT "FK_2ecdb33f23e9a6fc392025c0b97" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: social_requests FK_32be8e6bd24d59149ebc4b29f27; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_requests
    ADD CONSTRAINT "FK_32be8e6bd24d59149ebc4b29f27" FOREIGN KEY ("toUserId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: recharge_agent_applications FK_33f0416f1aa1aa1976046d27ec0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agent_applications
    ADD CONSTRAINT "FK_33f0416f1aa1aa1976046d27ec0" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_vips FK_3f312d4a4d8632e8b31f58802d3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_vips
    ADD CONSTRAINT "FK_3f312d4a4d8632e8b31f58802d3" FOREIGN KEY ("vipPlanId") REFERENCES public.vip_plans(id) ON DELETE CASCADE;


--
-- Name: profile_visits FK_41d42c9f76df6bacf81bdc16e5b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_visits
    ADD CONSTRAINT "FK_41d42c9f76df6bacf81bdc16e5b" FOREIGN KEY ("visitorId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: reports FK_4353be8309ce86650def2f8572d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT "FK_4353be8309ce86650def2f8572d" FOREIGN KEY ("reporterId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: chat_messages FK_45745953065384cc9c4264c2a3d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT "FK_45745953065384cc9c4264c2a3d" FOREIGN KEY ("conversationId") REFERENCES public.chat_conversations(id) ON DELETE CASCADE;


--
-- Name: room_bans FK_4ddcc53b3247c634c352603dc0c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_bans
    ADD CONSTRAINT "FK_4ddcc53b3247c634c352603dc0c" FOREIGN KEY ("roomId") REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: recharge_orders FK_5f8501202b13cd3f35e2929e01a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_orders
    ADD CONSTRAINT "FK_5f8501202b13cd3f35e2929e01a" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notifications FK_692a909ee0fa9383e7859f9b406; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT "FK_692a909ee0fa9383e7859f9b406" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: wallet_transactions FK_69454773f1e666a14c6a9539353; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT "FK_69454773f1e666a14c6a9539353" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: rooms FK_6c939085068c5539bad393be6b9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT "FK_6c939085068c5539bad393be6b9" FOREIGN KEY ("hostId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_moderators FK_6e2c115bfdba758d450a5dc6846; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_moderators
    ADD CONSTRAINT "FK_6e2c115bfdba758d450a5dc6846" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: gift_sends FK_70fe713ef5463e7aa66bbcb321d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_sends
    ADD CONSTRAINT "FK_70fe713ef5463e7aa66bbcb321d" FOREIGN KEY ("giftId") REFERENCES public.gifts(id) ON DELETE SET NULL;


--
-- Name: user_vips FK_776c4d82b2a8bdf328bc3f51cc9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_vips
    ADD CONSTRAINT "FK_776c4d82b2a8bdf328bc3f51cc9" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_cosmetics FK_7ac096a8bfe8bad3810cf3b01b1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_cosmetics
    ADD CONSTRAINT "FK_7ac096a8bfe8bad3810cf3b01b1" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_seats FK_8256f8fefaf42f4fdb204f58f40; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seats
    ADD CONSTRAINT "FK_8256f8fefaf42f4fdb204f58f40" FOREIGN KEY ("roomId") REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: user_profiles FK_8481388d6325e752cd4d7e26c6d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT "FK_8481388d6325e752cd4d7e26c6d" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: recharge_agents FK_891986e15af0e6c9936d3d25f92; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agents
    ADD CONSTRAINT "FK_891986e15af0e6c9936d3d25f92" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: profile_visits FK_8abdc3af0797198cc1edb6de942; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_visits
    ADD CONSTRAINT "FK_8abdc3af0797198cc1edb6de942" FOREIGN KEY ("profileUserId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: recharge_agent_ledger FK_8b5199ab654843357766592c0d2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recharge_agent_ledger
    ADD CONSTRAINT "FK_8b5199ab654843357766592c0d2" FOREIGN KEY ("agentId") REFERENCES public.recharge_agents(id) ON DELETE CASCADE;


--
-- Name: lucky_box_opens FK_8effac5e5a0f87cc71753d98e29; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_box_opens
    ADD CONSTRAINT "FK_8effac5e5a0f87cc71753d98e29" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_games FK_933c87654559418207c1c8b9612; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_games
    ADD CONSTRAINT "FK_933c87654559418207c1c8b9612" FOREIGN KEY ("playerOId") REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: lucky_reward_grants FK_9519ebc45afbce61f299349a408; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_reward_grants
    ADD CONSTRAINT "FK_9519ebc45afbce61f299349a408" FOREIGN KEY ("openId") REFERENCES public.lucky_box_opens(id) ON DELETE CASCADE;


--
-- Name: agency_applications FK_96b114455001b681fc42e21c24a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_applications
    ADD CONSTRAINT "FK_96b114455001b681fc42e21c24a" FOREIGN KEY ("reviewedById") REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: user_cosmetics FK_96c1bb2ccf043af338008825a77; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_cosmetics
    ADD CONSTRAINT "FK_96c1bb2ccf043af338008825a77" FOREIGN KEY ("cosmeticId") REFERENCES public.cosmetics(id) ON DELETE CASCADE;


--
-- Name: agency_members FK_a6bb192fadb7310808004f57535; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_members
    ADD CONSTRAINT "FK_a6bb192fadb7310808004f57535" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: withdraw_requests FK_a8f03d80e42da1162e25591dc4e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withdraw_requests
    ADD CONSTRAINT "FK_a8f03d80e42da1162e25591dc4e" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_moderators FK_b412281aebf17aeeff410e45790; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_moderators
    ADD CONSTRAINT "FK_b412281aebf17aeeff410e45790" FOREIGN KEY ("roomId") REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: blocks FK_b87401da081a7153ec3bd1e6ab6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT "FK_b87401da081a7153ec3bd1e6ab6" FOREIGN KEY ("blockedId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: drama_episodes FK_bbd9b86138c398837390bb21671; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drama_episodes
    ADD CONSTRAINT "FK_bbd9b86138c398837390bb21671" FOREIGN KEY ("seriesId") REFERENCES public.drama_series(id) ON DELETE CASCADE;


--
-- Name: room_games FK_bd3a636f5eb88d1273956ebcd98; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_games
    ADD CONSTRAINT "FK_bd3a636f5eb88d1273956ebcd98" FOREIGN KEY ("roomId") REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: room_bans FK_c816c4cce8f709165187b6fb2e5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_bans
    ADD CONSTRAINT "FK_c816c4cce8f709165187b6fb2e5" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_seats FK_ca59a2f22d45f00155364bbb6eb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seats
    ADD CONSTRAINT "FK_ca59a2f22d45f00155364bbb6eb" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: rooms FK_d7fa6127b9d1676d65c1785f7d7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT "FK_d7fa6127b9d1676d65c1785f7d7" FOREIGN KEY ("agencyId") REFERENCES public.agencies(id) ON DELETE SET NULL;


--
-- Name: female_identity_verifications FK_df653e18001df19542aa4410141; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.female_identity_verifications
    ADD CONSTRAINT "FK_df653e18001df19542aa4410141" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: devices FK_e8a5d59f0ac3040395f159507c6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT "FK_e8a5d59f0ac3040395f159507c6" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: agent_recharges FK_e91dc58a06bb3ef1e4c530d905a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_recharges
    ADD CONSTRAINT "FK_e91dc58a06bb3ef1e4c530d905a" FOREIGN KEY ("recipientUserId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_seat_signals FK_ea41e82e13c644166d9ea469fe4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seat_signals
    ADD CONSTRAINT "FK_ea41e82e13c644166d9ea469fe4" FOREIGN KEY ("roomId") REFERENCES public.rooms(id) ON DELETE CASCADE;


--
-- Name: blocks FK_ed8a33b2c1ac10922c2354f5cfc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT "FK_ed8a33b2c1ac10922c2354f5cfc" FOREIGN KEY ("blockerId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: follows FK_ef463dd9a2ce0d673350e36e0fb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT "FK_ef463dd9a2ce0d673350e36e0fb" FOREIGN KEY ("followingId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: lucky_box_opens FK_f09cdd61f21d144c689b8acbdbf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lucky_box_opens
    ADD CONSTRAINT "FK_f09cdd61f21d144c689b8acbdbf" FOREIGN KEY ("boxId") REFERENCES public.lucky_boxes(id) ON DELETE CASCADE;


--
-- Name: agencies FK_f1f8d72ccc44a6da7b7a2e8f773; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agencies
    ADD CONSTRAINT "FK_f1f8d72ccc44a6da7b7a2e8f773" FOREIGN KEY ("ownerId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: agent_recharges FK_f82f10254ad10ef1e6de447004b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_recharges
    ADD CONSTRAINT "FK_f82f10254ad10ef1e6de447004b" FOREIGN KEY ("agentId") REFERENCES public.recharge_agents(id) ON DELETE CASCADE;


--
-- Name: gift_sends FK_f9f7aa11e20e27f32fc6456551e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_sends
    ADD CONSTRAINT "FK_f9f7aa11e20e27f32fc6456551e" FOREIGN KEY ("senderId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_seat_signals FK_fa0749b418feb14a7f2757994ae; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_seat_signals
    ADD CONSTRAINT "FK_fa0749b418feb14a7f2757994ae" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: chat_participants FK_fb6add83b1a7acc94433d385692; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_participants
    ADD CONSTRAINT "FK_fb6add83b1a7acc94433d385692" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: chat_messages FK_fc6b58e41e9a871dacbe9077def; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT "FK_fc6b58e41e9a871dacbe9077def" FOREIGN KEY ("senderId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: follows FK_fdb91868b03a2040db408a53331; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT "FK_fdb91868b03a2040db408a53331" FOREIGN KEY ("followerId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: room_host_follows FK_febd82d58a786b258d29c209523; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_host_follows
    ADD CONSTRAINT "FK_febd82d58a786b258d29c209523" FOREIGN KEY ("followerId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: chat_participants FK_ffa48c8c78e4c4d0cb29bd6d123; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_participants
    ADD CONSTRAINT "FK_ffa48c8c78e4c4d0cb29bd6d123" FOREIGN KEY ("conversationId") REFERENCES public.chat_conversations(id) ON DELETE CASCADE;


--
-- Name: agency_follows agency_follows_agencyId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_follows
    ADD CONSTRAINT "agency_follows_agencyId_fkey" FOREIGN KEY ("agencyId") REFERENCES public.agencies(id) ON DELETE CASCADE;


--
-- Name: agency_follows agency_follows_userId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agency_follows
    ADD CONSTRAINT "agency_follows_userId_fkey" FOREIGN KEY ("userId") REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict xRbOqoW1VyVsYzhOdr6jCPXF0WA9TLxGDRSN9G0h5RAYKYvaSATGMBWIDg2J5dz

