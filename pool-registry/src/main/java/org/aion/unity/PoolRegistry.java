package org.aion.unity;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.tooling.abi.Initializable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;
import java.util.Map;

/**
 * A stake delegation registry manages a list of registered pools, votes/un-votes on delegator's behalf, and
 * ensure the delegators receive its portion of the block rewards.
 * <p>
 * Workflow for pool operator:
 * - Register a staker
 * - Register the staker as a pool
 * - Set the coinbase address to the one controlled by the pool registry
 * - Register the pool registry as a listener to the staker
 */
public class PoolRegistry {

    // TODO: replace object graph-based collections with key-value storage
    // TODO: add restriction to operations based on pool state
    // TODO: add any necessary getters
    // TODO: allow pool operator to update meta data and commission rate
    // TODO: replace long with BigInteger

    @Initializable
    private static Address stakerRegistry;

    private static byte[] poolCoinbaseContract;

    private static Map<Address, PoolState> pools = new AionMap<>();

    static {
        poolCoinbaseContract = hexStringToByteArray("00001a4e504b03041400080808003150024f000000000000000000000000140004004d4554412d494e462f4d414e49464553542e4d46feca0000f34dcccb4c4b2d2ed10d4b2d2acecccfb35230d433e0e5f24dccccd375ce492c2eb652c82f4ad74b044ae995e6659654ea05e4e7e738e767e6252516a7a6f272f1720100504b0708fbf738714400000043000000504b03041400080808003150024f000000000000000000000000220000006f72672f61696f6e2f756e6974792f506f6f6c436f696e62617365652e636c6173737d545b73d35610fe4eec44b6500209a10990e2505a2a3b05f5420a2531102750629498c6345768e7583e28076429d52533f91f7de181bef615ca8cf1b40c8fed4cff533bdda378929861fab267cf6a77bfdd6ff7e8ef7f7f7f0be06bfcc0301184aec565e05b892fe33deb7e1078f381f41b3c1242036338f184ef72cbe3be6bd51a4f84136bc8308cf4385e563e0cc60e1957842ba338dc6318b4f96ecb9a6b36431145330c03b392306e3064cce2aa811cf23ab2d019b2f1b68c18ced9ff570c25c8c521f7a3c7226418367b92578bab0c43ca52f102e7a9b3cda5afe10443de15f13cf73c1573dc2cf6041918c1491dc3183530002d8f3e7cc070ec888b86712a5bfc94708fea3b65daef723153dc34700667759cc604831692af0c05b5646eaa1ecfa1a000261946d3d0168fb7ad8a7417fd58b822d4f01105ed722f11b5c70ca7cd6ad17e9f1f95fa313ed1710117897a6a6945b4a843e9bbb77d11ba7b29a55503268a0aae44f80e35cd70a597a6f727dfaa54f789591151e2c504f6192ea93c9719f443bb86cf894f19d513c7a164292675ff25bed2f105aed0b7503872470a3f26d2782b4894c2aa7409d304b4204761689eb38ed75d890b07a3571e4924424f362cde90d65c657141384153b1354b6c51f70b3ce68ae2e256c5c00ddc54b5de62e837b72afb8b55d151c63ced6e338dacf9a2cb8081db6ae465dca17845e181cf9288b783e6326fd1f046cde29149d7e390882656165155a1f77218ea7916fb0e1a9675d4d4160c1ea0da81ef1af84e4da58c15034338aed3963d4829d915616c6055bd8261ac513df314445b6a4b5f2c27ad86081ff0864796113ba069aef250aa7bd738588fb9f37489ef74ef7a3d484247dc919ec079c2c8d20b27ab5a6cd2b2a4d37b23b941378b4e46677fe9358ebd24a50f9b240752e310b6481afb0e740ed299579577839f9355252f7770ea378cb5f16107e74b7f42cbfe8a6c86f442fee75f0e6fa542079f66dee43a98eac05abed4c634591568060f49e6901dbdc50c1a9f2ae10c15018c13ec599213b88802a6318959ea4995354951044dff2ed595d2aee21a15364d79bec175fa9ac3cc419f85d41b38f907ca1b1dccbdc6c2461bdfbec2d8dacbf4431e7771b3ebbc4ed07d745e3f74be576ac3b6a7fe82460dbc981a596ae3fe338c9554921f49d41fadaf75f0bdfad8c13a7b9152fe3065afef1f8c6b286ba84d90ed51ca71ff7f504b07084c6340e37403000079050000504b03041400080808003150024f0000000000000000000000002b0000006f72672f61696f6e2f61766d2f757365726c69622f6162692f414249457863657074696f6e2e636c6173738d504d4bc340107d53d344626b3fb4157af3207e41f7e6a552d0aa50091eacf6bea94b5c4936926c8a7fcb93e0c11fe08f12a7a960c18b7b989d79f3de63663ebfde3f009c6087b0976691903a3542ce1351e42a8b752864a8c5d9f9f8f265a69e2d373d10a1f724e752c4d244e2b63056276aa5bf4668ad2afa0b32a1c9865ac65395e50cde8f2f08745dc5f2b1a77baa8db64342e720f8b59fd84c9b687038adc1c7860f073582631f754ed80ffe35f180e0252acf65a408edbfde84fa2835b995c64e655c30c919a50ffc35026dd44d91842abb93615caad319af207911ae7f407f9216d94c5de9586117151e91f7410f2ef856687355c13a67b4d880e3162343c62afcbb47c76fa8bf969c6d8e7e897aa8b2a2c35977c9c2261ae59d5c34d12abdbaa5a6fa0d504b0708491bfa8128010000c1010000504b03041400080808003150024f000000000000000000000000290000006f72672f61696f6e2f61766d2f757365726c69622f6162692f4142494465636f6465722e636c617373ad9b097c54d5bdc7ff67669219269364b210024960020249580261dfb36b62586c804ae2c22499240393199c4cd8b48a2d280ab880280828c54a000511855045acdd9ee2ab5b5b6b5fb5cf3e6bd5576bd1aad59a36fd9dbbcdb93313bc4ef948e6ce3df79cf3ff9deff99fffff9c9b78ee5f4f9d25a2a9ac82d18840b0add8ed0df88bddab3b8abb3a3d419fb7a9d8dde42d2e2bafa9f434075a3c412b3146ce15eed5ee629fdbdf56bcb06985a739642533a3d470adf1bc02a301e5cb16575d3bbfacfe7246acc60c3b7d6a617d4d43152fb031b2b4b843a86c6a2c67645b15e8f486208151e26cafdf1b9acb28a1a0b1bc7029237341e15207a590d34e164a73501239069089321c944c56fe6d20fa0ab57b3b198dac3330945918488bf475be27d41e6859e0eef030ca2c28ac0b0faf3e14f4fadb503355aeb9d0ef918b1c944343ec309acbc8de21b44f8fd5da195966251706dbe609d5b7078221696cf56004e90cd7443ca8f1cbc53528f232b2a2a82ee06f93ca6a51e6c30fae0378b9c7df166ac7e00b6a78f591171a7bd5da66cf2a0ed84ac58c665702bdabd5ebf1b5b85a029e4e973f1072b5bb577b5c1e7fa0abadddd5b42e84529fa735e40a055c418fbbc5e5f6bbdcc1a07bdd781b4d6434b0207abcf2344db2d3049aeca0e13482739aca686e1cd6f87c6af6a6631ea43a8b3cc12a9fa7c3c31925494f5506c9da3c95a3a2440b6e95dedcee695eb9a0cbe7abea58155ac77538682ef72413cd6334331e0c52214495312a5ce0591b727964452eafdfd512eecf2bf7a655afd0bc8e6b0c047c1eb75f92d9c0684e7c3ae44ed0f7a58cc61a92a2b580bba46b6a2adadd417773c81394f440e8acb8f474729746dff319151951a3d65fc82825bccae475114685f5e069e3d2e2f122f8ac576e0f3b5860e30ce8129b2c71d0281acdbde5dba28bc90b323eeff1a12d7a6e30e83d4af5ab1c5448455cc83522ad6a5fc02d878bea7867ad9577010b4d06674daddf8235198e0092102bb5421ce8957b439d8b038a381e9daa1dd44e5e3bb5d10a31a45606ba9a7cf262ad8c3328a126ef0382b09ac6181980d660952e3ecb5aac044f7372e8f21854859682dac24a0785a8cb4e9db45a5c3d3cde94f150c46b15f26436488a3af3bd7e393855078272b674d07a39f45ccfa8d868e8d082e0771c348ec6f3e637e913cbbace90a7c34a37232948759b03aba0a5480cd0b2fd5935318a6a78d4fe1e6db4d3776913ba685287632507c27c64c812460a177634e94a4d8d0d56da0a00d1a14568870033d9c8e89bd5b61a82bbe05ecd113d9a1a2bacb48351863e8408f6b0ee27188e479aadfb90df3b859e4c8df556ba5f24a24426c112a2eaa46f126134630f60538032cd548d95be2ffa180f38829d5a83fec3fd58b3f13026d717eec7d4586ba543223769bd0a56aa0d7293228266e651706b157a3235565be931ecaf2256bd60086b7fa2f195ab597a027b8016b12f5363a5954e190c039dd29e05dd9c8e37f2683d3cc5685ebc5b1cad93337cef946aa77c3a8b752517ab9b9b24f996471aec1413e53b314996b5b4043d9d78968a5d2cdffd2905b38ca63cb75c1f3a7e1677a60df7f15f7c77165661a517ec748e8fcbea56852647c874284f943186bda5a45208b009f0161e6173fb89b0523507bd2a87d9d71895187185924a7da4fd9583d6d23adec3eb8c5c15812e54e635654d910d6cf406b6e4c811a32f78f8d036e018ac1983b0d29b62a0c43075a1541a2922ec14a303905b6b6378db41b7d1ed7c0cff87035e3f6310dbd8e88f923044f03f311a2c088b8ce1923404f16906a545c7f1ff77d01d742717f7218e2db1c545b4b2d147885fbc505101a908fc1f8b01b9a4520cfd92cc7a6311991bd487ffcf1cb493eee512ffce283fb644a1858dbe942421477ca59f567d969044d5189fd6c844d1e7a0bdb40fb218eb775a756d6ccc2c09abb1b204fdb212728aa4aad6f86a11f30a1be0a087e8075c5252bfab25dcc0c692253db55696aa9f3b31fd4882aa8dcf9d2e05b10c071da147b8a281fdce9dd0c2c6064992aaad6c30a32c41922e53499a2a0dee5d60419fad589e831ea7135cd430c95aa595e58b13229fa1155b03612bd6fb044369524e0f9ae1918c86c56020d6b2b1d15a92511464c4b06f6545e29429c15b69e0e492f531ddd85e4809fc9adef10efa09fd94839a1073f6225ad85849387b285a52f44aac6c0ab6328b835e4f0bcf574a47adc1400738f8bb7c3e5720e8f2f017048242289916e7c9dcef0a48d9085dcc6054f19f74a15101cee48a80bf33e4f68796ba7d5dfc4c52816120e5d779fd9e055d1d4d9ee062b7745849af0b34bb7d4bdd412fbf570a93eb436ea44cf72ae5de5e1fe80a367baabd3e0ff61c26b210e1670825127fd537077726b2e17e8070cfc3611239a4efc928271a498c6f5b50632eee16a2dc846b6a510fa5178d39499945e69394f5386fc0e6e1335d329382cf5474ecc4b734568a922cb9190da26c22e91b3783b44a832141ee7c8dd2791e2b7a92321f2687995fcfee271babeb265bd129caab1b731c15ccac0c9f7632a7597ac962a57cc1b48bd230da2c1a4139345232ed223365e067280d833974af88c88131b9ccc45f6529223ad00b17318edbc64fd624a76ded8ed939eaad65f9a434eabb71c38ed945cb709bb01cc3cf793462fc85187711648cc17f6385f18f534ca7c0ec25125a137ffda098dea7985e229a8eb6ac2b4c885598a814ca12ad1112b907104dc0fc4ec4b84b681a4da21a9a4cf5344590ba44919a4c975101f710941662509254e6a004e22f799f14a56e2a1d16a175d3cd4edbba706942ccd2c498a5d698a5b698a5ced85d38b53e4a25104e1b2731eca846a2083e443403d33113d3310b9fb36911cda1569a4b37d03cda41a5d44d657482ca05324f6a9378904faf4406e776651277810b273343f25d2ea325e100253f4d1396a597f4d09467b172a6cd093fcccd69571fcfe08f731fd51cdc4aa6ec5e1aca8459ab86d54b61bd165e7c39acd661f616695ecec7a22eb019180d9fb53ccac5c8d015becfc6d864953b307f7c2d5c0e35a53194964b4a39470eaec1c2c14db23ca83cad8cf534ac1b5e9c9b2788be12250d58198d9072351cef1a485b2e8976c82214d18caa304059e0038ac0fa7e045e164b42822ab036d6d3dd64b57493c52c02b6e4e665958a80dba17505b4ae84d60e68f543eb2a416bbda6f572c097b56e56b496eab526aa5a17c45293a86a5da43ac5f6fe09ae45c97aa8ba1eaabe03553742d5064155a9a6ea0a2d90ddaaa89af74d5459a354f52f6a334a6e87a82d10b50da2ee80a8bb0451f33451dfd2425c3fa26caaa8c5b144d954514b155157f62f6a174aee87a83d10b50fa21e80a8fd31452d5383198e51b2a832bd28e7005555632c554e6d06af56645d7b5494953754907508254790851ec17a3c8a987b0c6bf1b820ab4c93b59cdc8aac2d8aac8a7e5835c754a5cda04785759a561ed3f112859d46c953e0f534783d83847016c1e24782b00a4d980feb4116b6551156d91faf404c65da345ea7f23a4d6b1eeb17d9f3283907642f02d92f80ec25207b455056a929c3515e51d6aba4a5b592b21eba81d5851354c97e7209f9c999b69f9ce11cde8de1442a4e5315df2829b6f4d08639b9676c5a9f63ccb9a7e916a9492e6f216c4e32c9dc4b531286f6f1ad9795ffdb0ca97c7425d2f6ea0d8ceeb7d87efd0e29e84d24d8b7680afd2fc2fadbb498de41b47c1751f24f60fe1eada60fb400dfa005731e13e4f1db11a36e55f652b76141ca09ba9a0737be488d90488f20b1388a447a4c12963af3dcbcdc0374c998bcfe82ede28d66d6ddf79640a694121532497cef0632db70d2a53e4c73f856baf471afd08accd2e54b4a94284ec5c684e8638cf11350fc14143e03c5cf41f10b50fc12147b41eb5fa0d847d80bd3065c6fc149b9544afb162cb351d2b686331dab31ddac31bd11cb6fabc4f40eba53d8f470a65d46986644302d8b629aa132bd5b629a20334d5098a681294f0a4b2480e7058083c91201f01e0ef04b4a0f93617804bd36964276964a05cc89cd5d3ad5b20c5accb2a88165d3356c30c80ca10e5c3b599e40264323335823d3a5915949dbb17de06476d2bd0a9924854cc80899cc0832a55164325532bb44320314324e99cc5209ccc71704b34706932680190930a300a600600a01a60860c602cc3880990030250033096026930fd7208e886130e91a986c0d4c4803b3827623f570307b917ae20033f06bc10c54c13c2881b1ca60ec3a30572e3400e64034987900530a30e5005301309500530d3097024c2dc0d401cc7c80590030f301e68a6f00663f7d5f02f310fd209eb594f5b56b294b05735002e3b4c964927464ae5d7401320e85cce16832cb40a60164ae0299ab41e61a90590e326e9069a146d64ad7b2366a65ed584b6d584b2b35326e8d4ca340465c4bddd81a703247b03590c9a42864d618213328824c451499412a99a3a2cb242a60326497c116e10a89cd4717f49ae3329b4c814d27d884c06635d8ac019bb560b31e6cae079b1be1351be03537c36bbe4b015cbbd826c16b066a5e93a3b159a3b1e9c026e93189cde33880c96c52153686f27bf6d7e6f76c95cd933aafb1eae0f05dcab72e0047759c9e6838db00e70ec0b90b70ee069ced80730fe0ec049c5d709cfbe1387be0387b71c4d843abd98382e30cd41c272766ca3f49a7243839eaeb13f66d05ce712370f223e0dc1005275f85f34375371771047b3ad669567df88c74963d635ba01a1d17b1577a9af2978deba167eb74501324a8837a2917d9ff1265e794afec9caa290ddfb0ab608701f511407d14508f02ea63807a1c504f50137b02f1e9245dc77ae826769a6e6367b06d780699e02c3dcc9ea563ec390df275380eff884602ed36d8780e33c9f78ec735c807e9c7d2eb21c65f572a902f517698f7a9907588878b8887efa7f43062fe0aab390af27015f2cff5909d2e15e4f3fc81d375c6365fed7bacd9e952513a5d32cb73cbc6f6d08b224bacd92943b37b69b495ce49f466520af4bf007a2f82de4b348abd4c13d82ba0f71ae8fd12f45ea766f61bc4b0df21babf4937b3b7682bfb3ded646f6bc44a30f6ffc66e9c5110e7cc97408cbbe57d0ab19db8939f327a995e5188ad52dcf29042ec97116e394164365170d304718f9e28bb694b1441b4c8ee7fe3fe6b6103f10c6d16d2c129fa4dbd9c0eba29658154f9b7bc321032da8873e41e9c1e659463c9da4b55098503c28bfd2dbed8d3fa3060b3724fcc4a13fe019fe4accbc98991bf0fd61f80f59f51fa214d677fa12af657c4c1f3b48e7d0aeffc9c36b22f680bf6b4db71ddc5fe092ffd8af6b25e3ac8fa24e693c1ee162d87ec854ffe0f4e0c66ba09076a35281cd2fcf5019c03de94e8ff1e67898b403f3d3efada61e10f3afadb74f4df89a4ffae71faef45d07f2f067d145b4c36b299ec643725d1749383aa4c291430a5d23a5306dd641a481b4d83688b299bb6e3bacb944b7b4c4368af29870e9a86c6457f0b4e6c9cfefbf4c1c5a09f111f7ded58f1671dfd7b74f4ff1249ffafc6e97f1241ff9358f44781fe68d02f04fd22d01f03fae3407f3ce89780fe64d09f0afad3407f2ae8cf02fd19a03f13f4e7c445ff3c4e839cfedf701abc08f433e3a3af1d5d3ed7d1dfa3a3ff4524fd7f18a7df1b41bf3716fd2ad0af06fdcb40bf06f46b41bf0ef4e783fe15a05f0ffa4b407f29e82f01fd06d0bf12f49781fe5571d1df4d5f49f4ff8933b74c3fa8d03f120ffd8111f45b8dd1c7f9288503cd60a4c37f40c4cf4c1afed405726d8bc67f1346b2378abf5de3cf12f5fc71aff04f12f8b780bf07fcdbc0bf1dfcbde0bf12fc7db4deb48a369882b4c914a2ada62eda81eb6ed33ab05f43fb4c6be990e97a8dffad1aff7d48e732ff0d2851f91fd1f8efa7fd8cbf9363ccca6c17837f567cfcb334fe761dffc33afe8e28fe29c6f93b23f83b63f1ff1ef86f04ff5bc0ff56f0df0cfeb783ff16f0bf13fcef06ff1de07f0ff8ef00ff5de07f2ff8df07fef7c7c5bf1b6713ce3f8da55f0cfe83e2e33f48e39fa9e37f5cc73f2b8a7fb671fe4322f80f89c5ff00f83f04fe0f83ff41f0ef06ffc3e07f04fc8f81ff71f03f01fe4f80ff09f0ef01ff93e07f0afc7f1817ff636cb0c43f87e52afcaf53f81f8e877f76047f8f31fed91affa13afe3d3afeaefec3ffd7e21f1e817f78acf0ff1cf0ff18f87f0afc3f03fe9f03fff3c0ff02f0ff02f85f06fe5781ff35e07f15f85f07fe5f01ffafa9dbf4464cfc760d7f38fc1fd6f03f482759be847f040e4817c1fdf3e373ff7c0dff281dfe7c5df6cd8b72ff02e3fcc744f01f13cbfddf06ff3f80ff3be0ff47f07f17fcdf03fff7c1ff43f0ff08fccf83ffc7e07f1efc3f03ffbf81ffa770ffbfc7e3feac901549fcc7b2711783fff0f8f80fd7f817ebf89fd3b9ffc428fe938cf39f1ac17f6a2cfe7d64c119dd663691dd6ca6e9660b559913298016ebcd49b4c19c4c9bcca9b4d5eca41db8ee3667d25e733aed3367d02173565cfc27b329d2df01cc857df95751cb505bfa6b93c83f6651084de7637e9c84df7ea5c94b3885bf5f300fa134730ee59af384df78a97fb862e2ff9781626689626688ee058d6263a6de066a0e164cb828c59c4f83cd230413433413af5ec844a26662f6054d8c8289d1305118c30463e592a1847f03504b0708d6826f4acc120000a9380000504b010214001400080808003150024ffbf7387144000000430000001400040000000000000000000000000000004d4554412d494e462f4d414e49464553542e4d46feca0000504b010214001400080808003150024f4c6340e3740300007905000022000000000000000000000000008a0000006f72672f61696f6e2f756e6974792f506f6f6c436f696e62617365652e636c617373504b010214001400080808003150024f491bfa8128010000c10100002b000000000000000000000000004e0400006f72672f61696f6e2f61766d2f757365726c69622f6162692f414249457863657074696f6e2e636c617373504b010214001400080808003150024fd6826f4acc120000a93800002900000000000000000000000000cf0500006f72672f61696f6e2f61766d2f757365726c69622f6162692f4142494465636f6465722e636c617373504b0506000000000400040046010000f2180000000000000021220000000000000000000000000000000000000000000000000000000000000000");
    }

    @Callable
    public static Address getStakerRegistry() {
        requireNoValue();
        return stakerRegistry;
    }

    /**
     * Register a pool in the registry.
     *
     * @param metaData       the pool meta data
     * @param commissionRate the pool commission rate
     * @return the pool coinbase address
     */
    @Callable
    public static Address registerPool(byte[] metaData, int commissionRate) {
        // sanity check
        requireNonNull(metaData);
        require(commissionRate >= 0 && commissionRate <= 100);
        requireNoValue();

        // the caller doesn't have to register a staker beforehand.
        Address caller = Blockchain.getCaller();
        require(!pools.containsKey(caller));

        byte[] address = Blockchain.getAddress().toByteArray();
        System.arraycopy(address, 0, poolCoinbaseContract, poolCoinbaseContract.length - Address.LENGTH, Address.LENGTH);

        Result result = Blockchain.create(BigInteger.ZERO, poolCoinbaseContract, Blockchain.getRemainingEnergy());
        require(result.isSuccess());
        Address coinbaseAddress = new Address(result.getReturnData());

        PoolState ps = new PoolState(caller, coinbaseAddress, metaData, commissionRate);
        pools.put(caller, ps);

        return coinbaseAddress;
    }

    /**
     * Delegates stake to a pool.
     *
     * @param pool the pool address
     */
    @Callable
    public static void delegate(Address pool) {
        delegate(pool, Blockchain.getValue());
    }

    private static void delegate(Address pool, BigInteger value) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requirePositive(value);

        detectBlockRewards(pool);

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("vote")
                .encodeOneAddress(pool)
                .toBytes();
        secureCall(stakerRegistry, value, data, Blockchain.getRemainingEnergy());

        PoolState ps = pools.get(pool);
        BigInteger previousStake = getOrDefault(ps.delegators, caller, BigInteger.ZERO);
        ps.delegators.put(caller, previousStake.add(value));

        // update rewards state machine
        ps.rewards.onVote(caller, Blockchain.getBlockNumber(), value.longValue());
    }

    /**
     * Cancels stake to a pool.
     *
     * @param pool   the pool address
     * @param amount the amount of stake to undelegate
     */
    @Callable
    public static void undelegate(Address pool, long amount) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requirePositive(amount);
        requireNoValue();

        detectBlockRewards(pool);

        PoolState ps = pools.get(pool);
        BigInteger previousStake = getOrDefault(ps.delegators, caller, BigInteger.ZERO);
        BigInteger amountBI = BigInteger.valueOf(amount);
        require(previousStake.compareTo(amountBI) >= 0);
        ps.delegators.put(caller, previousStake.subtract(amountBI));

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("unvoteTo")
                .encodeOneAddress(pool)
                .encodeOneLong(amount)
                .encodeOneAddress(caller)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        // update rewards state machine
        ps.rewards.onUnvote(caller, Blockchain.getBlockNumber(), amount);
    }

    /**
     * Redelegate a pool using the rewards.
     *
     * @param pool the pool address
     */
    @Callable
    public static void redelegate(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        PoolState ps = pools.get(pool);
        long newStake = ps.rewards.onRevote(caller, Blockchain.getBlockNumber());
        if (newStake > 0) {
            ps.delegators.put(caller, getOrDefault(ps.delegators, caller, BigInteger.ZERO).add(BigInteger.valueOf(newStake)));

            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("vote")
                    .encodeOneAddress(pool)
                    .toBytes();
            secureCall(stakerRegistry, BigInteger.valueOf(newStake), data, Blockchain.getRemainingEnergy());

            Blockchain.println("re-vote: " + newStake);
        }
    }

    /**
     * Transfers stake from one pool to another.
     *
     * @param fromPool the from pool address
     * @param toPool   the to pool address
     * @param amount   the amount of stake to transfer
     */
    @Callable
    public static void transferStake(Address fromPool, Address toPool, long amount) {
        Address caller = Blockchain.getCaller();
        requirePool(fromPool);
        requirePool(toPool);
        requirePositive(amount);
        requireNoValue();

        detectBlockRewards(fromPool);
        detectBlockRewards(toPool);

        PoolState ps1 = pools.get(fromPool);
        BigInteger previousStake1 = getOrDefault(ps1.delegators, caller, BigInteger.ZERO);
        PoolState ps2 = pools.get(fromPool);
        BigInteger previousStake2 = getOrDefault(ps1.delegators, caller, BigInteger.ZERO);

        BigInteger amountBI = BigInteger.valueOf(amount);
        require(previousStake1.compareTo(amountBI) >= 0);
        ps1.delegators.put(caller, previousStake1.subtract(amountBI));
        ps2.delegators.put(caller, previousStake2.add(amountBI));

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("transferStake")
                .encodeOneAddress(fromPool)
                .encodeOneAddress(toPool)
                .encodeOneLong(amount)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

        // update rewards state machine
        ps1.rewards.onUnvote(caller, Blockchain.getBlockNumber(), amount);
        ps2.rewards.onVote(caller, Blockchain.getBlockNumber(), amount);
    }

    /**
     * Returns the stake of a delegator to a pool.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of stake
     */
    @Callable
    public static long getStake(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return getOrDefault(pools.get(pool).delegators, delegator, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the owner's stake to a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static long getSelfStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        PoolState ps = pools.get(pool);
        return getOrDefault(ps.delegators, ps.stakerAddress, BigInteger.ZERO).longValue();
    }

    /**
     * Returns the total stake of a pool.
     *
     * @param pool the pool address
     * @return the amount of stake
     */
    @Callable
    public static long getTotalStake(Address pool) {
        requirePool(pool);
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("getStakeByStakerAddress")
                .encodeOneAddress(pool)
                .toBytes();
        Result result = Blockchain.call(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
        require(result.isSuccess());
        return new ABIDecoder(result.getReturnData()).decodeOneLong();
    }

    /**
     * Releases the stake (locked coin) to the owner.
     *
     * @param owner the owner address
     * @param limit the max number of limited coins to process
     * @return the number of locked coins released, not the amount
     */
    @Callable
    public static void releaseStake(Address owner, int limit) {
        requireNonNull(owner);
        requirePositive(limit);
        requireNoValue();

        byte[] data = new ABIStreamingEncoder()
                .encodeOneString("releaseStake")
                .encodeOneAddress(owner)
                .encodeOneInteger(limit)
                .toBytes();
        secureCall(stakerRegistry, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());
    }

    /**
     * Returns the auto-redelegation fee set by the delegator, or -1 if not set.
     *
     * @param pool      the pool's address
     * @param delegator the delegator's address
     * @return the fee in percentage, or -1
     */
    @Callable
    public static int getAutoRedelegationFee(Address pool, Address delegator) {
        requirePool(pool);
        requireNoValue();

        return getOrDefault(pools.get(pool).autoDelegator, delegator, -1);
    }

    @Callable
    public static void enableAutoRedelegation(Address pool, int feePercentage) {
        requirePool(pool);
        require(feePercentage >= 0 && feePercentage <= 100);
        requireNoValue();

        pools.get(pool).autoDelegator.put(Blockchain.getCaller(), feePercentage);
    }

    @Callable
    public static void disableAutoRedelegation(Address pool) {
        requirePool(pool);
        requireNoValue();

        pools.get(pool).autoDelegator.remove(Blockchain.getCaller());
    }

    @Callable
    public static void autoRedelegate(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        detectBlockRewards(pool);

        PoolState ps = pools.get(pool);
        require(ps.autoDelegator.containsKey(delegator));

        // do a withdraw
        long amount = ps.rewards.onWithdraw(delegator, Blockchain.getBlockNumber());
        if (delegator.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        Blockchain.println("Auto delegation: rewards = " + amount);

        if (amount > 0) {
            long fee = amount * ps.autoDelegator.get(delegator) / 100;
            long remaining = amount - fee;

            Blockchain.println("Auto delegation: fee = " + fee + ", remaining = " + remaining);

            // transfer fee to the caller
            secureCall(Blockchain.getCaller(), BigInteger.valueOf(fee), new byte[0], Blockchain.getRemainingEnergy());

            // use the remaining rewards to delegate
            delegate(pool, BigInteger.valueOf(remaining));
        }
    }

    /**
     * Returns the outstanding rewards of a delegator.
     *
     * @param pool      the pool address
     * @param delegator the delegator address
     * @return the amount of outstanding rewards
     */
    public static long getRewards(Address pool, Address delegator) {
        requirePool(pool);
        requireNonNull(delegator);
        requireNoValue();

        return pools.get(pool).rewards.getRewards(delegator, Blockchain.getBlockNumber());
    }

    /**
     * Withdraws rewards from one pool
     *
     * @param pool the pool address
     */
    @Callable
    public static long withdraw(Address pool) {
        Address caller = Blockchain.getCaller();
        requirePool(pool);
        requireNoValue();

        detectBlockRewards(pool);

        // query withdraw amount from rewards state machine
        PoolState ps = pools.get(pool);
        long amount = ps.rewards.onWithdraw(caller, Blockchain.getBlockNumber());
        if (caller.equals(ps.stakerAddress)) {
            amount += ps.rewards.onWithdrawOperator();
        }

        // do a transfer
        if (amount > 0) {
            secureCall(caller, BigInteger.valueOf(amount), new byte[0], Blockchain.getRemainingEnergy());
        }
        return amount;
    }

    /**
     * Returns pool status.
     *
     * @param pool the pool address.
     * @return
     */
    @Callable
    public static String getPoolStatus(Address pool) {
        requirePool(pool);
        requireNoValue();
        return pools.get(pool).isActive ? "ACTIVE" : "BROKEN";
    }

    @Callable
    public static void onSigningAddressChange(Address staker, Address newSigningAddress) {
        onlyStakerRegistry();
        requireNonNull(newSigningAddress);
        requireNoValue();

        // do nothing
    }

    @Callable
    public static void onCoinbaseAddressChange(Address staker, Address newCoinbaseAddress) {
        onlyStakerRegistry();
        requireNonNull(newCoinbaseAddress);
        requireNoValue();

        PoolState ps = pools.get(staker);
        if (ps != null) {
            boolean isCoinbaseSetup = newCoinbaseAddress.equals(ps.coinbaseAddress);
            boolean isListenerSetup = true;
            boolean active = isCoinbaseSetup && isListenerSetup;

            if (!ps.isActive && active) {
                switchToActive(ps);
            }

            if (ps.isActive && !active) {
                switchToBroken(ps);
            }
        }
    }

    @Callable
    public static void onListenerAdded(Address staker) {
        onlyStakerRegistry();
        requireNoValue();

        PoolState ps = pools.get(staker);
        if (ps != null) {
            byte[] txData = new ABIStreamingEncoder()
                    .encodeOneString("getCoinbaseAddress")
                    .encodeOneAddress(staker)
                    .toBytes();
            Result result = Blockchain.call(stakerRegistry, BigInteger.ZERO, txData, Blockchain.getRemainingEnergy());
            require(result.isSuccess());
            Address coinbaseAddress = new ABIDecoder(result.getReturnData()).decodeOneAddress();

            boolean isCoinbaseSetup = coinbaseAddress.equals(ps.coinbaseAddress);
            boolean isListenerSetup = true;
            boolean active = isCoinbaseSetup && isListenerSetup;

            if (!ps.isActive && active) {
                switchToActive(ps);
            }

            if (ps.isActive && !active) {
                switchToBroken(ps);
            }
        }
    }

    @Callable
    public static void onListenerRemoved(Address staker) {
        onlyStakerRegistry();
        requireNoValue();

        PoolState ps = pools.get(staker);
        if (ps != null) {
            if (ps.isActive) {
                switchToBroken(ps);
            }
        }
    }

    private static void switchToActive(PoolState ps) {
        ps.isActive = true;
        ps.rewards.setCommissionRate(ps.commissionRate);
    }

    private static void switchToBroken(PoolState ps) {
        ps.isActive = false;
        ps.rewards.setCommissionRate(0);
    }

    private static void require(boolean condition) {
        Blockchain.require(condition);
    }

    private static void requireNonNull(Object obj) {
        require(obj != null);
    }

    private static void requireNoValue() {
        require(Blockchain.getValue().equals(BigInteger.ZERO));
    }

    private static void requirePool(Address pool) {
        require(pool != null && pools.containsKey(pool));
    }

    private static void requirePositive(BigInteger num) {
        require(num != null && num.compareTo(BigInteger.ZERO) > 0);
    }

    private static void requirePositive(long num) {
        require(num > 0);
    }

    private static void onlyStakerRegistry() {
        Address caller = Blockchain.getCaller();
        require(caller.equals(stakerRegistry));
    }

    public static byte[] hexStringToByteArray(String s) {
        // TODO: make static
        int[] map = new int[256];
        int value = 0;
        for (char c : "0123456789abcdef".toCharArray()) {
            map[c] = value++;
        }

        char[] chars = s.toCharArray();
        int length = chars.length;
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) ((map[chars[i]] << 4) + map[chars[i + 1]]);
        }
        return result;
    }

    private static void secureCall(Address targetAddress, BigInteger value, byte[] data, long energyLimit) {
        Result result = Blockchain.call(targetAddress, value, data, energyLimit);
        require(result.isSuccess());
    }


    private static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            return defaultValue;
        }
    }

    private static void detectBlockRewards(Address pool) {
        PoolState ps = pools.get(pool);

        BigInteger balance = Blockchain.getBalance(ps.coinbaseAddress);
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            byte[] data = new ABIStreamingEncoder()
                    .encodeOneString("transfer")
                    .encodeOneAddress(Blockchain.getAddress())
                    .encodeOneLong(balance.longValue())
                    .toBytes();
            secureCall(ps.coinbaseAddress, BigInteger.ZERO, data, Blockchain.getRemainingEnergy());

            ps.rewards.onBlock(Blockchain.getBlockNumber(), balance.longValue());

            Blockchain.println("New block rewards: " + balance);
        }
    }
}
