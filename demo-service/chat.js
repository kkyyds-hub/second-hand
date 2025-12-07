// chat.js  使用 CommonJS 语法
const OpenAI = require("openai");

// 从环境变量读取 API Key
const apiKey = process.env.OPENAI_API_KEY;

if (!apiKey) {
    console.error("请先在系统环境变量中配置 OPENAI_API_KEY，再运行本脚本。");
    process.exit(1);
}

const client = new OpenAI({ apiKey });

// 从命令行参数拿问题，例如：node chat.js "帮我解释一下 Java 里的 PasswordEncoder"
const userQuestion = process.argv.slice(2).join(" ");

if (!userQuestion) {
    console.error('请在命令后面加上你的问题，例如：node chat.js "帮我解释一下 Java 里的 PasswordEncoder"');
    process.exit(1);
}

async function main() {
    try {
        const response = await client.responses.create({
            model: "gpt-4.1-mini",
            input: userQuestion,
        });

        // 兼容 text / output_text 两种字段
        const outputBlocks = response.output[0].content;
        const text = outputBlocks
            .map(block => block.output_text || block.text || "")
            .join("\n");

        console.log("\n=== GPT 回复 ===\n");
        console.log(text.trim());
    } catch (err) {
        console.error("调用 OpenAI API 出错：", err.message || err);
    }
}

main();
