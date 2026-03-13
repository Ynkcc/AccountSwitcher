import re
import requests
import json
import os
from urllib.parse import urlparse, parse_qs, unquote, parse_qs

# 接口请求头
HEADERS = {
    'Referer': 'https://gp.qq.com/',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
}

def get_rank(points):
    """段位判断函数"""
    try:
        points = float(points)
    except:
        return '未知段位'

    if points >= 1200 and points < 1700:
        return '青铜'
    elif points >= 1700 and points < 2200:
        return '白银'
    elif points >= 2200 and points < 2700:
        return '黄金'
    elif points >= 2700 and points < 3200:
        return '铂金'
    elif points >= 3200 and points < 3700:
        return '钻石'
    elif points >= 3700 and points < 4200:
        return '皇冠'
    elif points >= 4200:
        if points >= 4300:
            stars = int((points - 4300) // 100) + 1
            return f'王牌 {stars}星'
        else:
            return '王牌'
    else:
        return '未知段位'

def process_response(response):
    """处理响应数据"""
    match = re.search(r"data:'(.*?)'", response)
    if match:
        encoded_data = match.group(1)
        decoded_data = unquote(encoded_data)
        # 解析 query string 为字典
        character_data = {k: v[0] for k, v in parse_qs(decoded_data).items()}
        
        # 提取必要字段，设置默认值
        charac_name = character_data.get('charac_name', '未知')
        level = character_data.get('level', '0')
        charac_no = character_data.get('charac_no', '未知')
        history_rank_times = character_data.get('historyhighestranktimes', '0')
        reli = character_data.get('reli', '0')
        rating = character_data.get('tppseasonduorating', '0')
        schemeindex = character_data.get('schemeindex', '0')
        isbanuser = character_data.get('isbanuser', '0')
        is_online = character_data.get('is_online', '0')
        accumulatechargenum = character_data.get('accumulatechargenum', '0')
        logintoday = character_data.get('logintoday', '0')
        lastlogouttime = int(character_data.get('lastlogouttime', '0'))

        import datetime
        last_logout_time_formatted = datetime.datetime.fromtimestamp(lastlogouttime).strftime('%Y-%m-%d %H:%M:%S') if lastlogouttime else "未知"

        # 构建结果字典
        result = {
            '角色名称': charac_name,
            '编号': charac_no,
            '账号等级': level,
            '是否在线': "在线" if is_online == "1" else "不在线",
            '是否封号': "已封号" if isbanuser == "1" else "账号正常",
            '是否人脸': "人脸号" if schemeindex == "1" else "无人脸",
            '王牌印记': history_rank_times,
            '热力值': reli,
            '段位': get_rank(rating),
            '段位积分': rating,
            '最后下线时间': last_logout_time_formatted,
            '充值总额': accumulatechargenum,
            '今日登录': f"{logintoday}次"
        }
        return result
    return None

def make_api_request(url, headers):
    """发送 API 请求并返回原始响应文本"""
    try:
        response = requests.get(url, headers=headers, timeout=15, verify=False)
        return response.text
    except Exception as e:
        return f"请求错误: {str(e)}"

def save_mapping(openid, data):
    """保存映射信息到本地文件"""
    mapping_file = 'account_mappings.json'
    mappings = {}
    if os.path.exists(mapping_file):
        try:
            with open(mapping_file, 'r', encoding='utf-8') as f:
                mappings = json.load(f)
        except:
            pass
    
    mappings[openid] = data
    
    try:
        with open(mapping_file, 'w', encoding='utf-8') as f:
            json.dump(mappings, f, ensure_ascii=False, indent=4)
        return True
    except:
        return False

def main():
    json_path = 'decrypted_data.json'
    if not os.path.exists(json_path):
        print(f"错误：找不到数据源文件 {json_path}")
        return

    with open(json_path, 'r', encoding='utf-8') as f:
        config = json.load(f)

    access_token = config.get('token')
    openid = config.get('openid')

    if not access_token or not openid:
        print("错误：无法从 decrypted_data.json 获取 access_token 或 openid")
        return

    # 设置请求特定的 Cookie
    headers = HEADERS.copy()
    headers['Cookie'] = f'acctype=qc; openid={openid}; access_token={access_token}; appid=1106467070'

    print(f"\n--- 正在查询 OpenID: {openid} ---\n")

    # 优先查询安卓区数据
    android_url = 'https://comm.aci.game.qq.com/main?game=cjm&area=2&platid=1&sCloudApiName=ams.gameattr.role'
    android_res = make_api_request(android_url, headers)
    
    result = process_response(android_res)
    if result:
        print(">>> 角色信息:")
        for k, v in result.items():
            print(f"{k}: {v}")
        
        # 保存映射
        success = save_mapping(openid, result)
        print(f"\n映射保存状态: {'成功' if success else '失败'}")
        print(f"当前角色ID: {result['编号']}")
    else:
        print("未能从响应中提取角色信息")
        print("原始响应内容:", android_res)

if __name__ == "__main__":
    import urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    main()

